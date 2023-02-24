package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * These tests assert architectural design invariants about the workspace role hierarchy. These are
 * not expected to change.
 */
public class CloudSyncRoleMappingTest extends BaseUnitTest {

  @Autowired CloudSyncRoleMapping cloudSyncRoleMapping;

  @Test
  void writerPermissionsContainReaderPermissions() {
    assertThat(
        cloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.READER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.WRITER)
                    .getIncludedPermissions()))));
  }

  @Test
  void applicationPermissionsContainWriterPermissions() {
    assertThat(
        cloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.APPLICATION)
                    .getIncludedPermissions()))));
  }

  @Test
  void ownerPermissionsContainWriterPermissions() {
    assertThat(
        cloudSyncRoleMapping
            .getCustomGcpProjectIamRoles()
            .get(WsmIamRole.WRITER)
            .getIncludedPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.OWNER)
                    .getIncludedPermissions()))));
  }

  @Test
  void getCustomGcpProjectIamRoles_dataprocDisabled() {
    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            not(
                in(
                    (cloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.READER)
                        .getIncludedPermissions())))));
    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            not(
                in(
                    (cloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.WRITER)
                        .getIncludedPermissions())))));
    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocOwnerPermissions(),
        everyItem(
            not(
                in(
                    (cloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.OWNER)
                        .getIncludedPermissions())))));
  }

  @Test
  void getCustomGcpProjectIamRoles_dataprocEnabled() {
    Mockito.when(mockFeatureConfiguration().isDataprocEnabled()).thenReturn(true);

    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.READER)
                    .getIncludedPermissions()))));
    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.WRITER)
                    .getIncludedPermissions()))));
    assertThat(
        cloudSyncRoleMapping.getAdditionalDataprocOwnerPermissions(),
        everyItem(
            in(
                (cloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.OWNER)
                    .getIncludedPermissions()))));
  }
}
