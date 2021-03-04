package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.IamRole;
import org.junit.jupiter.api.Test;

/**
 * These tests assert architectural design invariants about the workspace role hierarchy. These are
 * not expected to change.
 */
public class CloudSyncRoleMappingTest extends BaseUnitTest {
  @Test
  void writerPermissionsContainReaderPermissions() {
    assertThat(
        CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.WRITER),
        containsInAnyOrder(CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.READER)));
  }

  @Test
  void editorPermissionsContainWriterPermissions() {
    assertThat(
        CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.EDITOR),
        containsInAnyOrder(CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.WRITER)));
  }

  @Test
  void applicationPermissionsContainWriterPermissions() {
    assertThat(
        CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.APPLICATION),
        containsInAnyOrder(CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.WRITER)));
  }

  @Test
  void ownerPermissionsContainEditorPermissions() {
    assertThat(
        CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.OWNER),
        containsInAnyOrder(CloudSyncRoleMapping.CLOUD_SYNC_ROLE_MAP.get(IamRole.EDITOR)));
  }
}
