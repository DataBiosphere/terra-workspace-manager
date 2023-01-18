package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;

import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.AzureConnectedTestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(Lifecycle.PER_CLASS)
public class CreateAndDeleteAzureControlledResourceFlightTest extends BaseAzureConnectedTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private AzureConnectedTestUtils azureUtils;

  private Workspace sharedWorkspace;
  private UUID workspaceUuid;

  @BeforeAll
  public void setup() throws InterruptedException {
    sharedWorkspace =
        createWorkspaceWithCloudContext(workspaceService, userAccessUtils.defaultUserAuthRequest());
    workspaceUuid = sharedWorkspace.getWorkspaceId();
  }

  @AfterAll
  public void cleanup() {
    workspaceService.deleteWorkspace(sharedWorkspace, userAccessUtils.defaultUserAuthRequest());
  }

  @Test
  public void createAzureIpControlledResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("ip"))
                    .description(getAzureName("ip-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .ipName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .build();

    // Submit an IP creation flight and verify the instance is created.
    azureUtils.createResource(
        workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_IP);

    // clean up resources - delete ip resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        resource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        resource.getIpName(),
        azureTestUtils.getComputeManager().networkManager().publicIpAddresses()
            ::getByResourceGroup);
  }

  @Test
  public void createAndDeleteAzureRelayNamespaceControlledResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureRelayNamespaceCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureRelayNamespaceCreationParameters();

    final UUID resourceId = UUID.randomUUID();
    ControlledAzureRelayNamespaceResource resource =
        ControlledAzureRelayNamespaceResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("relay"))
                    .description(getAzureName("relay-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .namespaceName(creationParameters.getNamespaceName())
            .region(creationParameters.getRegion())
            .build();

    // Submit a relay creation flight and verify the resource exists in the workspace.
    azureUtils.createResource(
        workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_RELAY_NAMESPACE);

    // clean up resources - delete relay resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        resource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        resource.getNamespaceName(),
        azureTestUtils.getRelayManager().namespaces()::getByResourceGroup);
  }

  @Test
  public void createAzureDiskControlledResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureDiskCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureDiskResource resource =
        ControlledAzureDiskResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name(getAzureName("disk"))
                    .description(getAzureName("disk-desc"))
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .diskName(creationParameters.getName())
            .region(creationParameters.getRegion())
            .size(creationParameters.getSize())
            .build();

    // Submit a Disk creation flight and verify the resource exists in the workspace.
    azureUtils.createResource(
        workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_DISK);

    // clean up resources - delete disk resource
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        resource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        resource.getDiskName(),
        azureTestUtils.getComputeManager().disks()::getByResourceGroup);
  }

  @Test
  public void createAzureNetworkControlledResource() throws InterruptedException {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    final ApiAzureNetworkCreationParameters creationParams =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    // TODO: make this application-private resource once the POC supports it
    final UUID resourceId = UUID.randomUUID();
    ControlledAzureNetworkResource resource =
        ControlledAzureNetworkResource.builder()
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                    .workspaceUuid(workspaceUuid)
                    .resourceId(resourceId)
                    .name("testNetwork")
                    .description("testDesc")
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                    .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                    .build())
            .networkName(creationParams.getName())
            .region(creationParams.getRegion())
            .subnetName(creationParams.getSubnetName())
            .addressSpaceCidr(creationParams.getAddressSpaceCidr())
            .subnetAddressCidr(creationParams.getSubnetAddressCidr())
            .build();

    // Submit a Network creation flight and verify the resource exists in the workspace.
    azureUtils.createResource(
        workspaceUuid, userRequest, resource, WsmResourceType.CONTROLLED_AZURE_NETWORK);

    // clean up resources - delete network resource;
    azureUtils.submitControlledResourceDeletionFlight(
        workspaceUuid,
        userRequest,
        resource,
        azureTestUtils.getAzureCloudContext().getAzureResourceGroupId(),
        resource.getNetworkName(),
        azureTestUtils.getComputeManager().networkManager().networks()::getByResourceGroup);
  }
}
