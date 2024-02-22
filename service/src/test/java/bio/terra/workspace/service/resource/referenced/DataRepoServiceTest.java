package bio.terra.workspace.service.resource.referenced;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.service.datarepo.DataRepoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DataRepoServiceTest extends BaseSpringBootUnitTest {

  @Autowired private DataRepoService dataRepoService;

  @Test
  void testValidateInvalidDataRepoInstance() {
    assertThrows(
        ValidationException.class, () -> dataRepoService.getInstanceUrl("fake-invalid-test"));
  }

  @Test
  void testValidateValidDataRepoInstance() {
    try {
      // the valid k/v is set in test/resources/application.properties
      // we trim and toLowerCase the string, so this verifies that too
      dataRepoService.getInstanceUrl(" FaKe-VaLiD-tEsT  ");
    } catch (ValidationException e) {
      fail("Valid Data Repo instance was rejected.");
    }
  }
}
