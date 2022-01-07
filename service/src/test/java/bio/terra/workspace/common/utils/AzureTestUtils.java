package bio.terra.workspace.common.utils;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("azure-test")
@Component
public class AzureTestUtils {
  @Autowired private final AzureTestConfiguration azureTestConfiguration;
  @Autowired private final UserAccessUtils userAccessUtils;

  public AzureTestUtils(
      AzureTestConfiguration azureTestConfiguration, UserAccessUtils userAccessUtils) {
    this.azureTestConfiguration = azureTestConfiguration;
    this.userAccessUtils = userAccessUtils;
  }

  /** Creates a workspace, returning its workspaceId. */
  public UUID createWorkspace(WorkspaceService workspaceService) {
    Workspace request =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());
  }

  /** Create the FlightMap input parameters required for the {@link CreateAzureContextFlight}. */
  public FlightMap createAzureContextInputParameters(
      UUID workspaceId, AuthenticatedUserRequest userRequest) {
    AzureCloudContext azureCloudContext = getAzureCloudContext();
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.REQUEST.getKeyName(), azureCloudContext);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  public FlightMap createControlledResourceInputParameters(
      UUID workspaceId, AuthenticatedUserRequest userRequest, ControlledResource resource) {
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.REQUEST.getKeyName(), resource);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  public AzureCloudContext getAzureCloudContext() {
    return new AzureCloudContext(
        azureTestConfiguration.getTenantId(),
        azureTestConfiguration.getSubscriptionId(),
        azureTestConfiguration.getManagedResourceGroupId());
  }
}
