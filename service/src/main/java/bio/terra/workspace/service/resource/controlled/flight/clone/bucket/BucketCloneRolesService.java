package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.util.Strings;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BucketCloneRolesService {
  private static final Logger logger = LoggerFactory.getLogger(BucketCloneRolesService.class);
  private static final int MAX_RETRY_ATTEMPTS = 30;
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);
  private final CrlService crlService;

  @Autowired
  public BucketCloneRolesService(CrlService crlService) {
    this.crlService = crlService;
  }

  public void addBucketRoles(StorageTransferInput inputs, String transferServiceSAEmail)
      throws InterruptedException {
    addOrRemoveBucketIdentities(BucketPolicyIdentityOperation.ADD, inputs, transferServiceSAEmail);
  }

  public void removeBucketRoles(StorageTransferInput inputs, String transferServiceSAEmail)
      throws InterruptedException {
    addOrRemoveBucketIdentities(
        BucketPolicyIdentityOperation.REMOVE, inputs, transferServiceSAEmail);
  }

  /**
   * A utility method for flight steps, at least two of which need this exact implementation. Fetch
   * bucket details from the working map along with the correct project ID and remove the roles.
   */
  public void removeAllAddedBucketRoles(FlightMap workingMap) throws InterruptedException {
    final @Nullable StorageTransferInput sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, StorageTransferInput.class);
    final @Nullable StorageTransferInput destinationInputs =
        workingMap.get(
            ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS, StorageTransferInput.class);
    final @Nullable String transferServiceSAEmail =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, String.class);

    if (!Strings.isNullOrEmpty(transferServiceSAEmail)) {
      if (sourceInputs != null) {
        removeBucketRoles(sourceInputs, transferServiceSAEmail);
      }
      if (destinationInputs != null) {
        removeBucketRoles(destinationInputs, transferServiceSAEmail);
      }
    }
  }

  /**
   * Add or remove roles for an Identity.
   *
   * <p>NOTE: The previous implementation used GcpUtils.pollUntilEqual to compare the newPolicy
   * against the updated policy. That would never match, because the etag in the Policy object is
   * part of the equals evaluation. The updated comparison technique just looks at the specific
   * roles with the specific identity to see whether it is added or removed. This is not 100% safe,
   * since concurrent changes to the policy would be a problem. At this point in time, WSM is not
   * programmed to prevent concurrent access and that is outside the scope of this fix.
   *
   * @param operation - flag for add or remove
   * @param inputs - source or destination input object
   * @param transferServiceSAEmail - STS SA email address
   */
  private void addOrRemoveBucketIdentities(
      BucketPolicyIdentityOperation operation,
      StorageTransferInput inputs,
      String transferServiceSAEmail)
      throws InterruptedException {
    List<String> roles = inputs.getRoleNames();
    if (roles.isEmpty()) {
      // No-op
      return;
    }
    final StorageCow storageCow = crlService.createStorageCow(inputs.getProjectId());
    final Identity saIdentity = Identity.serviceAccount(transferServiceSAEmail);

    final Policy.Builder policyBuilder =
        storageCow.getIamPolicy(inputs.getBucketName()).toBuilder();
    for (String roleName : roles) {
      switch (operation) {
        case ADD -> policyBuilder.addIdentity(Role.of(roleName), saIdentity);
        case REMOVE -> policyBuilder.removeIdentity(Role.of(roleName), saIdentity);
      }
    }
    final Policy newPolicy = policyBuilder.build();
    Policy updatedPolicy = storageCow.setIamPolicy(inputs.getBucketName(), newPolicy);

    for (int i = 1; i <= MAX_RETRY_ATTEMPTS; i++) {
      int roleCount = countRoles(updatedPolicy, roles, saIdentity);
      if ((operation == BucketPolicyIdentityOperation.ADD && roleCount == roles.size())
          || (operation == BucketPolicyIdentityOperation.REMOVE && roleCount == 0)) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL.toMillis());
      logger.info("addRemoveBucketIdentities retry attempts: {}", i);
      updatedPolicy = storageCow.getIamPolicy(inputs.getBucketName());
    }
    throw new InternalServerErrorException("Bucket policy update propagation timed out");
  }

  private int countRoles(Policy testPolicy, List<String> roles, Identity saIdentity) {
    int counter = 0;
    for (var role : roles) {
      if (isRolePresent(testPolicy, role, saIdentity)) {
        counter++;
      }
    }
    return counter;
  }

  private boolean isRolePresent(Policy testPolicy, String roleName, Identity saIdentity) {
    if (testPolicy == null || testPolicy.getBindings() == null) {
      return false;
    }

    Set<Identity> identities = testPolicy.getBindings().get(Role.of(roleName));
    return (identities != null
        && identities.stream().anyMatch(identity -> identity.equals(saIdentity)));
  }

  private enum BucketPolicyIdentityOperation {
    ADD,
    REMOVE
  }
}
