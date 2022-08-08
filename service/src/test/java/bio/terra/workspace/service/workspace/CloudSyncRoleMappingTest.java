package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import org.junit.jupiter.api.Test;

/**
 * These tests assert architectural design invariants about the workspace role hierarchy. These are
 * not expected to change.
 */
public class CloudSyncRoleMappingTest extends BaseUnitTest {
  @Test
  void writerPermissionsContainReaderPermissions() {
    assertThat(
        CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
            .get(WsmIamRole.READER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
                    .get(WsmIamRole.WRITER)
                    .getIncludedPermissions()))));
  }

  @Test
  void applicationPermissionsContainWriterPermissions() {
    assertThat(
        CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
                    .get(WsmIamRole.APPLICATION)
                    .getIncludedPermissions()))));
  }

  @Test
  void ownerPermissionsContainWriterPermissions() {
    assertThat(
        CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (CloudSyncRoleMapping.CUSTOM_GCP_PROJECT_IAM_ROLES
                    .get(WsmIamRole.OWNER)
                    .getIncludedPermissions()))));
  }
}
