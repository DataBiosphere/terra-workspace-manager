package bio.terra.workspace.common;

import static bio.terra.workspace.service.features.FeatureService.AWS_APPLICATIONS_ENABLED;
import static bio.terra.workspace.service.features.FeatureService.AWS_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS connected tests: connected to AWS */
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles({"aws-connected-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {
  protected static final ApiCloudPlatform apiCloudPlatform = ApiCloudPlatform.AWS;

  @Autowired protected AwsCloudContextService awsCloudContextService;
  @Autowired protected AwsConfiguration awsConfiguration;
  @Autowired protected AwsConnectedTestUtils awsConnectedTestUtils;
  @MockBean protected FeatureService mockFeatureService;

  protected void enableFeature(String featureName, boolean enable) {
    when(mockFeatureService.isFeatureEnabled(eq(featureName), isNotNull())).thenReturn(enable);
    when(mockFeatureService.isFeatureEnabled(eq(featureName), isNull())).thenReturn(false);
    when(mockFeatureService.isFeatureEnabled(featureName)).thenReturn(false);

    doCallRealMethod().when(mockFeatureService).featureEnabledCheck(eq(featureName), any());
    doCallRealMethod().when(mockFeatureService).featureEnabledCheck(featureName);
  }

  protected void enableFeature(String featureName) {
    enableFeature(featureName, true);
  }

  protected void disableFeature(String featureName) {
    enableFeature(featureName, false);
  }

  @BeforeAll
  public void init() throws Exception {
    enableFeature(AWS_ENABLED);
    enableFeature(AWS_APPLICATIONS_ENABLED);
  }
}
