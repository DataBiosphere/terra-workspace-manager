package bio.terra.workspace.common;

import static org.mockito.Mockito.when;

import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS unit tests: not connected to AWS */
@Tag("aws-unit")
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles({"aws-unit-test", "unit-test"})
public class BaseAwsUnitTest extends BaseUnitTestMocks {

  @MockBean private ControlledResourceService mockControlledResourceService;

  @BeforeAll
  public void init() throws Exception {
    when(mockFeatureService().awsEnabled()).thenReturn(true);
  }

  public ControlledResourceService getMockControlledResourceService() {
    return mockControlledResourceService;
  }
}
