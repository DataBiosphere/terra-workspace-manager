package bio.terra.workspace.service.resource.controlled.mappings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.WsmResourceType;
import org.junit.jupiter.api.Test;

public class CustomGcpIamRoleTest extends BaseUnitTest {

  /**
   * GCP custom IAM role names are defined individually for each GCP project at project creation
   * time, with no mechanism for backfilling changes. If a change breaks this test, it may break
   * existing projects that use these names
   */
  @Test
  public void validateCustomIamRoleNames() {
    assertEquals(
        "GCS_BUCKET_READER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.READER)
            .getRoleName());
    assertEquals(
        "GCS_BUCKET_WRITER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.WRITER)
            .getRoleName());
    assertEquals(
        "GCS_BUCKET_EDITOR",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.EDITOR)
            .getRoleName());
    assertEquals(
        "GCS_BUCKET_ASSIGNER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.ASSIGNER)
            .getRoleName());

    assertEquals(
        "BIG_QUERY_DATASET_READER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.READER)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_WRITER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.WRITER)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_EDITOR",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.EDITOR)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_ASSIGNER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.ASSIGNER)
            .getRoleName());
  }
}
