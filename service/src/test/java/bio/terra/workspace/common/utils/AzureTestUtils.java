package bio.terra.workspace.common.utils;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.flight.create.azure.CreateAzureContextFlight;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.relay.RelayManager;
import com.azure.resourcemanager.storage.StorageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("azure-test")
@Component
public class AzureTestUtils {
  @Autowired final AzureTestConfiguration azureTestConfiguration;
  @Autowired private final UserAccessUtils userAccessUtils;
  @Autowired private CrlService crlService;
  @Autowired private AzureConfiguration azureConfiguration;

  public AzureTestUtils(
      AzureTestConfiguration azureTestConfiguration, UserAccessUtils userAccessUtils) {
    this.azureTestConfiguration = azureTestConfiguration;
    this.userAccessUtils = userAccessUtils;
  }

  public Workspace createWorkspace(WorkspaceService workspaceService) {
    UUID uuid = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId(uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .spendProfileId(new SpendProfileId(UUID.randomUUID().toString()))
            .build();
    workspaceService.createWorkspace(workspace, null, userAccessUtils.defaultUserAuthRequest());
    return workspace;
  }

  public ComputeManager getComputeManager() {
    return crlService.getComputeManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public RelayManager getRelayManager() {
    return crlService.getRelayManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public StorageManager getStorageManager() {
    return crlService.getStorageManager(getAzureCloudContext(), this.azureConfiguration);
  }

  /** Create the FlightMap input parameters required for the {@link CreateAzureContextFlight}. */
  public FlightMap createAzureContextInputParameters(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    AzureCloudContext azureCloudContext = getAzureCloudContext();
    FlightMap inputs = new FlightMap();
    inputs.put(JobMapKeys.REQUEST.getKeyName(), azureCloudContext);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  public FlightMap createControlledResourceInputParameters(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest, ControlledResource resource) {
    FlightMap inputs = new FlightMap();
    inputs.put(ResourceKeys.RESOURCE, resource);
    inputs.put(ResourceKeys.RESOURCE_NAME, resource.getName());
    inputs.put(ResourceKeys.RESOURCE_TYPE, resource.getResourceType());
    inputs.put(ResourceKeys.RESOURCE_ID, resource.getResourceId().toString());
    inputs.put(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.CREATE);
    inputs.put(ResourceKeys.STEWARDSHIP_TYPE, StewardshipType.CONTROLLED);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    return inputs;
  }

  public FlightMap createControlledResourceInputParameters(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource,
      ApiAzureVmCreationParameters creationParameters) {
    var inputs = createControlledResourceInputParameters(workspaceUuid, userRequest, resource);
    if (creationParameters != null) {
      inputs.put(
          WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    }
    return inputs;
  }

  public FlightMap deleteControlledResourceInputParameters(
      UUID workspaceUuid,
      UUID resourceId,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource) {
    FlightMap inputs = new FlightMap();
    List<ControlledResource> resources = new ArrayList<>();
    resources.add(resource);
    inputs.put(CONTROLLED_RESOURCES_TO_DELETE, resources);
    inputs.put(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceUuid.toString());
    inputs.put(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, resourceId.toString());
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
