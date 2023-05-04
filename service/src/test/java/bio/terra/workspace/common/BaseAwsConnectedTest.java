package bio.terra.workspace.common;

import bio.terra.common.flagsmith.FlagsmithService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS connected tests: connected to AWS */
@Tag("aws")
@ActiveProfiles({"aws-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {
  @MockBean private FlagsmithService flagsmithService;

  @BeforeEach
  void init() {
    Mockito.when(flagsmithService.isFeatureEnabled("terra__aws_enabled"))
        .thenReturn(Optional.of(true));
  }
}
