package bio.terra.workspace.common;

import static bio.terra.workspace.common.utils.AwsTestUtils.SAM_USER_AWS_DISABLED;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
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

  @BeforeAll
  public void init() throws Exception {
    when(mockFeatureService.isFeatureEnabled(
            eq(FeatureService.AWS_ENABLED), not(eq(SAM_USER_AWS_DISABLED.getEmail()))))
        .thenReturn(true);
    when(mockFeatureService.isFeatureEnabled(
            eq(FeatureService.AWS_ENABLED), eq(SAM_USER_AWS_DISABLED.getEmail())))
        .thenReturn(false);

    doCallRealMethod().when(mockFeatureService).featureEnabledCheck(anyString(), notNull());
    doThrow(new FeatureNotSupportedException("exception"))
        .when(mockFeatureService)
        .featureEnabledCheck(anyString());
  }
}
