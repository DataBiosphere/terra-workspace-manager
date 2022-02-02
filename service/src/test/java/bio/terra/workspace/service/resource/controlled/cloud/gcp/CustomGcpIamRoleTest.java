package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import org.junit.jupiter.api.Test;

public class CustomGcpIamRoleTest extends BaseUnitTest {

  /**
   * GCP custom IAM role names are defined individually for each GCP project at project creation
   * time, with no mechanism for backfilling changes. If a change breaks this test, it may break
   * existing projects that use these names.
   */
  @Test
  public void validateCustomIamRoleNames() {
    assertEquals(
        "GCS_BUCKET_READER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET, ControlledResourceIamRole.READER)
            .getRoleName());
    assertEquals(
        "GCS_BUCKET_WRITER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET, ControlledResourceIamRole.WRITER)
            .getRoleName());
    assertEquals(
        "GCS_BUCKET_EDITOR",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET, ControlledResourceIamRole.EDITOR)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_READER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET, ControlledResourceIamRole.READER)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_WRITER",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET, ControlledResourceIamRole.WRITER)
            .getRoleName());
    assertEquals(
        "BIG_QUERY_DATASET_EDITOR",
        CustomGcpIamRoleMapping.CUSTOM_GCP_RESOURCE_IAM_ROLES
            .get(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET, ControlledResourceIamRole.EDITOR)
            .getRoleName());
  }
}
