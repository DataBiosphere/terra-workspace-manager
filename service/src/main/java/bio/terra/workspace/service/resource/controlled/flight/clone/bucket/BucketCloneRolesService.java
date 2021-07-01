package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.workspace.service.crl.CrlService;
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BucketCloneRolesService {

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

  private enum BucketPolicyIdentityOperation {
    ADD,
    REMOVE
  }

  /**
   * Add or remove roles for an Identity. TODO: move somewhere reusable
   *
   * @param operation - flag for add or remove
   * @param inputs - source or destination input object
   * @param transferServiceSAEmail - STS SA email address
   */
  private void addOrRemoveBucketIdentities(
      BucketPolicyIdentityOperation operation,
      BucketCloneInputs inputs,
      String transferServiceSAEmail) {
    final StorageCow storageCow = crlService.createStorageCow(inputs.getProjectId());
    final Identity saIdentity = Identity.serviceAccount(transferServiceSAEmail);

    final Policy.Builder policyBuilder =
        storageCow.getIamPolicy(inputs.getBucketName()).toBuilder();
    for (String roleName : inputs.getRoleNames()) {
      switch (operation) {
        case ADD:
          policyBuilder.addIdentity(Role.of(roleName), saIdentity);
          break;
        case REMOVE:
          policyBuilder.removeIdentity(Role.of(roleName), saIdentity);
          break;
      }
    }
    storageCow.setIamPolicy(inputs.getBucketName(), policyBuilder.build());
  }

}
