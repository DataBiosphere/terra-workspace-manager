package bio.terra.workspace.common.utils;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CONTROLLED_RESOURCES_TO_DELETE;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.spendprofile.model.SpendProfileOrganization;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.management.AzureEnvironment;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
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
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null).spendProfileId(getSpendProfileId()).build();
    workspaceService.createWorkspace(
        workspace, null, null, null, userAccessUtils.defaultUserAuthRequest());
    return workspace;
  }

  public ComputeManager getComputeManager() {
    return crlService.getComputeManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public MsiManager getMsiManager() {
    return crlService.getMsiManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public PostgreSqlManager getPostgreSqlManager() {
    return crlService.getPostgreSqlManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public ContainerServiceManager getContainerServiceManager() {
    return crlService.getContainerServiceManager(getAzureCloudContext(), this.azureConfiguration);
  }

  public StorageManager getStorageManager() {
    return crlService.getStorageManager(getAzureCloudContext(), this.azureConfiguration);
  }

  /**
   * Create the FlightMap input parameters required for the {@link
   * bio.terra.workspace.service.workspace.flight.create.cloudcontext.CreateCloudContextFlight}.
   */
  public FlightMap createAzureContextInputParameters(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    return WorkspaceFixtures.createCloudContextInputs(
        workspaceUuid, userRequest, CloudPlatform.AZURE, getSpendProfile());
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
    inputs.put(ResourceKeys.RESOURCE_STATE_RULE, WsmResourceStateRule.DELETE_ON_FAILURE);
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      inputs.put(
          ControlledResourceKeys.PRIVATE_RESOURCE_IAM_ROLE, ControlledResourceIamRole.EDITOR);
    }
    return inputs;
  }

  public <T> FlightMap createControlledResourceInputParameters(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      ControlledResource resource,
      T creationParameters) {
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
        new AzureCloudContextFields(
            azureTestConfiguration.getTenantId(),
            azureTestConfiguration.getSubscriptionId(),
            azureTestConfiguration.getManagedResourceGroupId(),
            AzureEnvironment.AZURE),
        new CloudContextCommonFields(
            getSpendProfileId(), WsmResourceState.READY, /* flightId= */ null, /* error= */ null));
  }

  public SpendProfileId getSpendProfileId() {
    return new SpendProfileId(azureTestConfiguration.getSpendProfileId());
  }

  public SpendProfile getSpendProfile() {
    return new SpendProfile(
        getSpendProfileId(),
        CloudPlatform.AZURE,
        null,
        UUID.fromString(azureTestConfiguration.getTenantId()),
        UUID.fromString(azureTestConfiguration.getSubscriptionId()),
        azureTestConfiguration.getManagedResourceGroupId(),
        new SpendProfileOrganization(false));
  }
}
