package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * These tests assert architectural design invariants about the workspace role hierarchy. These are
 * not expected to change.
 */
public class CloudSyncRoleMappingTest extends BaseSpringBootUnitTest {

  @Autowired GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping;

  @Test
  void writerPermissionsContainReaderPermissions() {
    assertThat(
        gcpCloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.READER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.WRITER)
                    .getIncludedPermissions()))));
  }

  @Test
  void applicationPermissionsContainWriterPermissions() {
    assertThat(
        gcpCloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.APPLICATION)
                    .getIncludedPermissions()))));
  }

  @Test
  void ownerPermissionsContainWriterPermissions() {
    assertThat(
        gcpCloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.OWNER)
                    .getIncludedPermissions()))));
  }
}
