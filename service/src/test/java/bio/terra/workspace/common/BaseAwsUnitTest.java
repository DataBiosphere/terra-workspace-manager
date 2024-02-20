package bio.terra.workspace.common;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SAM_USER;
import static bio.terra.workspace.service.features.FeatureService.AWS_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

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
public class BaseAwsUnitTest extends BaseSpringBootUnitTestMocks {

  @BeforeAll
  public void init() throws Exception {
    when(mockFeatureService().isFeatureEnabled(eq(AWS_ENABLED), isNotNull())).thenReturn(true);
    when(mockFeatureService().isFeatureEnabled(eq(AWS_ENABLED), isNull())).thenReturn(false);
    when(mockFeatureService().isFeatureEnabled(AWS_ENABLED)).thenReturn(false);

    doCallRealMethod().when(mockFeatureService()).featureEnabledCheck(eq(AWS_ENABLED), any());
    doCallRealMethod().when(mockFeatureService()).featureEnabledCheck(AWS_ENABLED);

    when(mockSamService().getSamUser((AuthenticatedUserRequest) any())).thenReturn(SAM_USER);
  }
}
