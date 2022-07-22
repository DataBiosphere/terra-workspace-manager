package bio.terra.workspace.common.utils;

import bio.terra.workspace.common.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ControllerValidationUtilsTest extends BaseUnitTest {

  @Test
  void userFacingIdCanStartWithNumber() throws Exception {
    ControllerValidationUtils.validateUserFacingId("1000-genomes");
  }
}
