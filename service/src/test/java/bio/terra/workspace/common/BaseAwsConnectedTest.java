package bio.terra.workspace.common;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.service.features.FeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS connected tests: connected to AWS */
@ActiveProfiles({"aws-connected-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {
  protected static final ApiCloudPlatform apiCloudPlatform = ApiCloudPlatform.AWS;

  @Autowired protected AwsConfiguration awsConfiguration;
  @Autowired protected FeatureService featureService;
  @Autowired protected AwsTestUtils awsTestUtils;
}
