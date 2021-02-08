package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import org.junit.jupiter.api.Test;

public class ControllerValidationUtilsTest extends BaseUnitTest {

  // TODO(PF-404): we're supporting the existing CreateDataReferenceRequest.reference field to
  // migrate clients, but this test should be cleaned up with that field.
  @Test
  public void testCreateDataReferenceDeprecatedReference() {
    DataRepoSnapshot snapshotReference =
        new DataRepoSnapshot().snapshot("snapshot-name").instanceName("instance-name");
    CreateDataReferenceRequestBody deprecatedRequest =
        new CreateDataReferenceRequestBody()
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference);
    ControllerValidationUtils.validate(deprecatedRequest);
  }
}
