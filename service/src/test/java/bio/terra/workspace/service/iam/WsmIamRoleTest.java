package bio.terra.workspace.service.iam;

import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import org.junit.jupiter.api.Test;

public class WsmIamRoleTest extends BaseUnitTest {

  /**
   * This test looks trivial, but it enforces that WsmIamRole.fromSam must handle unknown values
   * without throwing. Sam can sometimes return roles that WSM doesn't know about (e.g. if WSM lists
   * the roles on a RAWLS_WORKSPACE stage workspace), and WSM should silently ignore them instead of
   * throwing exceptions.
   */
  @Test
  public void missingApiModelReturnsNull() {
    assertNull(WsmIamRole.fromSam("FAKE_VALUE"));
  }
}
