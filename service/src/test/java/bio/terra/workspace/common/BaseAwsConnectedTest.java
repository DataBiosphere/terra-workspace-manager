package bio.terra.workspace.common;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.service.features.FeatureService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS connected tests: connected to AWS */
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles({"aws-connected-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {
  protected static final ApiCloudPlatform apiCloudPlatform = ApiCloudPlatform.AWS;

  @Autowired protected AwsConfiguration awsConfiguration;
  @Autowired protected AwsTestUtils awsTestUtils;
  @MockBean protected FeatureService featureService;

  @BeforeAll
  public void setup() {
    Mockito.when(featureService.awsEnabled()).thenReturn(true);
  }
}
