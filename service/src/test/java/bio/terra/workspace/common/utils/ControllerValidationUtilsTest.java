package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiCreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshot;
import bio.terra.workspace.generated.model.ApiReferenceTypeEnum;
import org.junit.jupiter.api.Test;

public class ControllerValidationUtilsTest extends BaseUnitTest {

  // TODO(PF-404): we're supporting the existing CreateDataReferenceRequest.reference field to
  // migrate clients, but this test should be cleaned up with that field.
  @Test
  public void testCreateDataReferenceDeprecatedReference() {
    ApiDataRepoSnapshot snapshotReference =
        new ApiDataRepoSnapshot().snapshot("snapshot-name").instanceName("instance-name");
    ApiCreateDataReferenceRequestBody deprecatedRequest =
        new ApiCreateDataReferenceRequestBody()
            .referenceType(ApiReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference);
    ControllerValidationUtils.validate(deprecatedRequest);
  }
}
