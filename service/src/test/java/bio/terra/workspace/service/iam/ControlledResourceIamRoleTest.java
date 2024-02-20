package bio.terra.workspace.service.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiControlledResourceIamRole;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import org.junit.jupiter.api.Test;

public class ControlledResourceIamRoleTest extends BaseUnitTest {

  @Test
  public void validateFromApiModel() {
    assertEquals(
        ControlledResourceIamRole.READER,
        ControlledResourceIamRole.fromApiModel(ApiControlledResourceIamRole.READER));
    assertEquals(
        ControlledResourceIamRole.WRITER,
        ControlledResourceIamRole.fromApiModel(ApiControlledResourceIamRole.WRITER));
    assertEquals(
        ControlledResourceIamRole.EDITOR,
        ControlledResourceIamRole.fromApiModel(ApiControlledResourceIamRole.EDITOR));

    assertThrows(InternalLogicException.class, () -> ControlledResourceIamRole.fromApiModel(null));
  }
}
