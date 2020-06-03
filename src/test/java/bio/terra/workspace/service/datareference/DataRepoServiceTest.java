package bio.terra.workspace.service.datareference;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.datarepo.DataRepoService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @Test
  public void testValidateInvalidDataRepoInstance() throws Exception {

    DataRepoService testService = new DataRepoService();

    assertThrows(
        ValidationException.class,
        () -> {
          testService.validateInstance("foo");
        });
  }

  @Test
  public void testValidateValidDataRepoInstance() throws Exception {

    DataRepoService testService = new DataRepoService();

    try {
      testService.validateInstance("https://data.terra.bio");
    } catch (ValidationException e) {
      fail("Valid Data Repo instance was rejected.");
    }
  }
}
