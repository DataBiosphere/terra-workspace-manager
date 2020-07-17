package bio.terra.workspace.service.trace;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.Main;
import io.opencensus.common.Scope;
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
class StackdriverTraceTest {

  @Autowired private StackdriverTrace trace;

  @Test
  void createScope() {
    String span1Name = "span1";
    Scope scope = trace.scope(span1Name);
  }

  @Test
  void annotate() {}
}
