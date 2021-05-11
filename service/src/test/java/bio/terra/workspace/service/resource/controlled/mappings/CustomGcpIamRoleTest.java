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
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.READER)
            .getRoleName(),
        "GCS_BUCKET_READER");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.WRITER)
            .getRoleName(),
        "GCS_BUCKET_WRITER");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.EDITOR)
            .getRoleName(),
        "GCS_BUCKET_EDITOR");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.GCS_BUCKET, ControlledResourceIamRole.ASSIGNER)
            .getRoleName(),
        "GCS_BUCKET_ASSIGNER");

    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.READER)
            .getRoleName(),
        "BIG_QUERY_DATASET_READER");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.WRITER)
            .getRoleName(),
        "BIG_QUERY_DATASET_WRITER");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.EDITOR)
            .getRoleName(),
        "BIG_QUERY_DATASET_EDITOR");
    assertEquals(
        CustomGcpIamRoleMapping.CUSTOM_GCP_IAM_ROLES
            .get(WsmResourceType.BIG_QUERY_DATASET, ControlledResourceIamRole.ASSIGNER)
            .getRoleName(),
        "BIG_QUERY_DATASET_ASSIGNER");
  }
}
