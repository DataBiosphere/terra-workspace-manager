package bio.terra.workspace.service.datareference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DataRepoServiceTest {

  @Autowired private DataRepoService dataRepoService;

  @Test
  public void testValidateInvalidDataRepoInstance() throws Exception {
    assertThrows(
        ValidationException.class,
        () -> {
          dataRepoService.validateInstance("https://fake-invalid-data-repo-url.broadinstitute.org");
        });
  }

  @Test
  public void testValidateValidDataRepoInstance() throws Exception {
    try {
      // the valid url is set in test/resources/application.properties
      // we trim and toLowerCase the string, so this verifies that too
      dataRepoService.validateInstance(" https://FaKe-VaLiD-data-repo-url.broadinstitute.org  ");
    } catch (ValidationException e) {
      fail("Valid Data Repo instance was rejected.");
    }
  }
}
