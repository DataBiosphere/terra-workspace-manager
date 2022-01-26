package bio.terra.workspace.service.resource.controlled.azure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Test to make sure things properly do not work when Azure feature is not enabled
public class AzureDisabledTest extends BaseConnectedTest {
  @Autowired private WorkspaceService workspaceService;
  @Autowired private JobService jobService;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private ControlledResourceService controlledResourceService;

  private static String getAzureName(String tag) {
    final String id = UUID.randomUUID().toString().substring(0, 6);
    return String.format("wsm-integ-%s-%s", tag, id);
  }

  @Test
  public void azureDisabledTest() throws InterruptedException {
    Workspace request =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    UUID workspaceId =
        workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    final UUID uuid = UUID.randomUUID();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            workspaceService.createAzureCloudContext(
                workspaceId, uuid.toString(), userRequest, null, null));

    assertThrows(
        FeatureNotSupportedException.class,
        () -> workspaceService.getAuthorizedAzureCloudContext(workspaceId, userRequest));

    final ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    ControlledAzureIpResource ipResource =
        ControlledAzureIpResource.builder()
            .workspaceId(workspaceId)
            .resourceId(uuid)
            .name(getAzureName("ip"))
            .description(getAzureName("ip-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .ipName(ipCreationParameters.getName())
            .region(ipCreationParameters.getRegion())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createIp(
                ipResource, ipCreationParameters, null, userRequest));

    final ApiAzureDiskCreationParameters diskCreationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    ControlledAzureDiskResource diskResource =
        ControlledAzureDiskResource.builder()
            .workspaceId(workspaceId)
            .resourceId(uuid)
            .name(getAzureName("disk"))
            .description(getAzureName("disk-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .diskName(diskCreationParameters.getName())
            .region(diskCreationParameters.getRegion())
            .size(diskCreationParameters.getSize())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createDisk(
                diskResource, diskCreationParameters, null, userRequest));

    final ApiAzureNetworkCreationParameters networkCreationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    ControlledAzureNetworkResource networkResource =
        ControlledAzureNetworkResource.builder()
            .workspaceId(workspaceId)
            .resourceId(uuid)
            .name(getAzureName("network"))
            .description(getAzureName("network-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .networkName(networkCreationParameters.getName())
            .region(networkCreationParameters.getRegion())
            .subnetName(networkCreationParameters.getSubnetName())
            .addressSpaceCidr(networkCreationParameters.getAddressSpaceCidr())
            .subnetAddressCidr(networkCreationParameters.getSubnetAddressCidr())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createNetwork(
                networkResource, networkCreationParameters, null, userRequest));

    final ApiAzureVmCreationParameters vmCreationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    ControlledAzureVmResource vmResource =
        ControlledAzureVmResource.builder()
            .workspaceId(workspaceId)
            .resourceId(uuid)
            .name(getAzureName("vm"))
            .description(getAzureName("vm-desc"))
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .vmName(vmCreationParameters.getName())
            .vmSize(vmCreationParameters.getVmSize())
            .vmImageUri(vmCreationParameters.getVmImageUri())
            .region(vmCreationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createVm(
                vmResource, vmCreationParameters, null, null, null, userRequest));
  }
}
