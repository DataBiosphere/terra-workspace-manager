package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.common.BaseAwsConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

// AWS SageMaker Notebook creation on the cloud may take 15+ min, combine tests where possible
@TestInstance(Lifecycle.PER_CLASS)
public abstract class BaseAwsSageMakerNotebookFlightTest extends BaseAwsConnectedTest {
  @Autowired protected AwsCloudContextService awsCloudContextService;
  @Autowired protected WsmResourceService wsmResourceService;
  @Autowired protected ControlledResourceService controlledResourceService;
  @Autowired protected JobService jobService;
  @Autowired protected StairwayComponent stairwayComponent;
  @Autowired protected MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired protected UserAccessUtils userAccessUtils;
  @Autowired protected CliConfiguration cliConfiguration;

  protected AuthenticatedUserRequest userRequest;
  protected UUID workspaceUuid;
  protected Environment environment;
  protected AwsCredentialsProvider awsCredentialsProvider;

  @BeforeAll
  public void init() throws Exception {
    super.init();
    userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    workspaceUuid =
        mvcWorkspaceApi.createWorkspaceAndWait(userRequest, apiCloudPlatform).getWorkspaceId();
    environment = awsCloudContextService.discoverEnvironment();
    awsCredentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());
    cliConfiguration.setServerName("verily-devel");
  }

  @AfterAll
  public void cleanUp() throws Exception {
    mvcWorkspaceApi.deleteWorkspaceAndWait(userRequest, workspaceUuid);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceUuid, userRequest);
  }

  protected ControlledAwsSageMakerNotebookResource makeResource(
      ApiAwsSageMakerNotebookCreationParameters creationParameters) {
    return ControlledAwsResourceFixtures.makeAwsSageMakerNotebookResourceBuilder(
            workspaceUuid,
            /* resourceName= */ creationParameters.getInstanceName(),
            /* instanceName= */ creationParameters.getInstanceName(),
            userRequest.getEmail())
        .build();
  }
}
