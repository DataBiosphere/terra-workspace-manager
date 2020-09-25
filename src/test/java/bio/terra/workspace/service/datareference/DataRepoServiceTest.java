package bio.terra.workspace.service.datareference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DataRepoServiceTest extends BaseUnitTest {

  @Autowired private DataRepoService dataRepoService;

  @Test
  public void testValidateInvalidDataRepoInstance() throws Exception {
    assertThrows(
        ValidationException.class,
        () -> {
          dataRepoService.getInstanceUrl("fake-invalid-test");
        });
  }

  @Test
  public void testValidateValidDataRepoInstance() throws Exception {
    try {
      // the valid k/v is set in test/resources/application.properties
      // we trim and toLowerCase the string, so this verifies that too
      dataRepoService.getInstanceUrl(" FaKe-VaLiD-tEsT  ");
    } catch (ValidationException e) {
      fail("Valid Data Repo instance was rejected.");
    }
  }
}
