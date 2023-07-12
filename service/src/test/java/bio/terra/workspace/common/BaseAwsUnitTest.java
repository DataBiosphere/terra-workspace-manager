package bio.terra.workspace.common;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS unit tests: not connected to AWS */
@Tag("aws-unit")
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles({"aws-unit-test", "unit-test"})
public class BaseAwsUnitTest extends BaseUnitTestMocks {

  @BeforeAll
  public void init() throws Exception {
    Mockito.when(mockFeatureService().awsEnabled()).thenReturn(true);
  }
}
