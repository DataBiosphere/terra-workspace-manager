package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest.AuthType;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.CreateGcpContextFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("azure-test")
@Component
public class AzureTestUtils {
  private final AzureTestConfiguration azureTestConfiguration;

  public AzureTestUtils(AzureTestConfiguration azureTestConfiguration) {
    this.azureTestConfiguration = azureTestConfiguration;
  }

  /** Creates a workspace, returning its workspaceId. */
  public UUID createWorkspace(WorkspaceService workspaceService) {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, defaultUserAuthRequest());
  }

  /** Create the FlightMap input parameters required for the {@link CreateGcpContextFlight}. */
  public FlightMap createInputParameters(UUID workspaceId, AuthenticatedUserRequest userRequest) {
    AzureCloudContext azureCloudContext = getAzureCloudContext();
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.REQUEST.getKeyName(), azureCloudContext);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  /** Expose the default test user email. */
  public String getDefaultUserEmail() {
    return azureTestConfiguration.getDefaultUserEmail();
  }

  /** Expose the second test user email. */
  public String getSecondUserEmail() {
    return azureTestConfiguration.getSecondUserEmail();
  }

  /** Provides an AuthenticatedUserRequest using the default user's email and access token. */
  public AuthenticatedUserRequest defaultUserAuthRequest() {
    return new AuthenticatedUserRequest()
        .email(getDefaultUserEmail())
        .subjectId(azureTestConfiguration.getDefaultUserObjectId())
        .authType(AuthType.BASIC);
  }

  /** Provides an AuthenticatedUserRequest using the second user's email and access token. */
  public AuthenticatedUserRequest secondUserAuthRequest() {
    return new AuthenticatedUserRequest()
        .email(getSecondUserEmail())
        .subjectId(azureTestConfiguration.getSecondUserObjectId())
        .authType(AuthType.BASIC);
  }

  public AzureCloudContext getAzureCloudContext() {
    return new AzureCloudContext(
        azureTestConfiguration.getTenantId(),
        azureTestConfiguration.getSubscriptionId(),
        azureTestConfiguration.getManagedResourceGroupId());
  }
}
