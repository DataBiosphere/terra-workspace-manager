package bio.terra.workspace.service.iam;

import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import org.junit.jupiter.api.Test;

public class WsmIamRoleTest extends BaseUnitTest {

  @Test
  public void missingApiModelReturnsNull() {
    assertNull(WsmIamRole.fromSam("FAKE_VALUE"));
  }

}
