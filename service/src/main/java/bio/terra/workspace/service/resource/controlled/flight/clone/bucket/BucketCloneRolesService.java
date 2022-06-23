package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.util.Strings;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BucketCloneRolesService {

  private static final int MAX_RETRY_ATTEMPTS = 30;
  private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);
  private final CrlService crlService;

  @Autowired
  public BucketCloneRolesService(CrlService crlService) {
    this.crlService = crlService;
  }

  public void addBucketRoles(BucketCloneInputs inputs, String transferServiceSAEmail) {
    addOrRemoveBucketIdentities(BucketPolicyIdentityOperation.ADD, inputs, transferServiceSAEmail);
  }

  public void removeBucketRoles(BucketCloneInputs inputs, String transferServiceSAEmail) {
    addOrRemoveBucketIdentities(
        BucketPolicyIdentityOperation.REMOVE, inputs, transferServiceSAEmail);
  }

  /**
   * A utility method for flight steps, at least two of which need this exact implementation. Fetch
   * bucket details from the working map along with the correct project ID and remove the roles.
   */
  public void removeAllAddedBucketRoles(FlightMap workingMap) {
    final @Nullable BucketCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, BucketCloneInputs.class);
    final @Nullable BucketCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, BucketCloneInputs.class);
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

  private enum BucketPolicyIdentityOperation {
    ADD,
    REMOVE
  }

  /**
   * Add or remove roles for an Identity.
   *
   * @param operation - flag for add or remove
   * @param inputs - source or destination input object
   * @param transferServiceSAEmail - STS SA email address
   */
  private void addOrRemoveBucketIdentities(
      BucketPolicyIdentityOperation operation,
      BucketCloneInputs inputs,
      String transferServiceSAEmail) {
    if (inputs.getRoleNames().isEmpty()) {
      // No-op
      return;
    }
    final StorageCow storageCow = crlService.createStorageCow(inputs.getProjectId());
    final Identity saIdentity = Identity.serviceAccount(transferServiceSAEmail);

    final Policy.Builder policyBuilder =
        storageCow.getIamPolicy(inputs.getBucketName()).toBuilder();
    for (String roleName : inputs.getRoleNames()) {
      switch (operation) {
        case ADD -> policyBuilder.addIdentity(Role.of(roleName), saIdentity);
        case REMOVE -> policyBuilder.removeIdentity(Role.of(roleName), saIdentity);
      }
    }
    final Policy newPolicy = policyBuilder.build();
    storageCow.setIamPolicy(inputs.getBucketName(), newPolicy);

    // verify the role changes have propagated, as we may need to work on this bucket immediately
    Policy updatedPolicy = storageCow.getIamPolicy(inputs.getBucketName());
    try {
      int retryAttempts = 0;
      while (!updatedPolicy.equals(newPolicy) && retryAttempts++ < MAX_RETRY_ATTEMPTS) {
        TimeUnit.MILLISECONDS.sleep(RETRY_INTERVAL.toMillis());
        updatedPolicy = storageCow.getIamPolicy(inputs.getBucketName());
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while verifying bucket policy.", e);
    }
  }
}
