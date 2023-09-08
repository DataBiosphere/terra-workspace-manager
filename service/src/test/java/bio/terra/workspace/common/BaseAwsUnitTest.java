package bio.terra.workspace.common;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SAM_USER;
import static bio.terra.workspace.common.utils.AwsTestUtils.SAM_USER_AWS_DISABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS unit tests: not connected to AWS */
@Tag("aws-unit")
@TestInstance(Lifecycle.PER_CLASS)
@ActiveProfiles({"aws-unit-test", "unit-test"})
public class BaseAwsUnitTest extends BaseUnitTestMocks {

  @BeforeAll
  public void init() throws Exception {
    when(mockFeatureService().isFeatureEnabled(eq(FeatureService.AWS_ENABLED), anyString()))
        .thenReturn(true);
    when(mockFeatureService()
            .isFeatureEnabled(FeatureService.AWS_ENABLED, SAM_USER_AWS_DISABLED.getEmail()))
        .thenReturn(false);
    when(mockFeatureService().isFeatureEnabled(FeatureService.AWS_ENABLED)).thenReturn(false);

    doCallRealMethod().when(mockFeatureService()).featureEnabledCheck(any(), any());
    doCallRealMethod().when(mockFeatureService()).featureEnabledCheck(any());

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any())).thenReturn(SAM_USER);
  }
}
