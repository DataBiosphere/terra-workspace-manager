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

  @Test
  void getCustomGcpProjectIamRoles_dataprocDisabled() {
    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            not(
                in(
                    (gcpCloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.READER)
                        .getIncludedPermissions())))));
    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocWriterPermissions(),
        everyItem(
            not(
                in(
                    (gcpCloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.WRITER)
                        .getIncludedPermissions())))));
    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocWriterPermissions(),
        everyItem(
            not(
                in(
                    (gcpCloudSyncRoleMapping
                        .getCustomGcpProjectIamRoles()
                        .get(WsmIamRole.OWNER)
                        .getIncludedPermissions())))));
  }

  @Test
  void getCustomGcpProjectIamRoles_dataprocEnabled() {
    Mockito.when(mockFeatureConfiguration().isDataprocEnabled()).thenReturn(true);

    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocReaderPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.READER)
                    .getIncludedPermissions()))));
    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocWriterPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.WRITER)
                    .getIncludedPermissions()))));
    assertThat(
        gcpCloudSyncRoleMapping.getAdditionalDataprocWriterPermissions(),
        everyItem(
            in(
                (gcpCloudSyncRoleMapping
                    .getCustomGcpProjectIamRoles()
                    .get(WsmIamRole.OWNER)
                    .getIncludedPermissions()))));
  }
}
