package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureNetworkCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
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
    UUID uuid = UUID.randomUUID();
    Workspace request =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId("a" + uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    UUID workspaceUuid =
        workspaceService.createWorkspace(request, userAccessUtils.defaultUserAuthRequest());

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    final UUID uuid = UUID.randomUUID();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            workspaceService.createAzureCloudContext(
                workspaceUuid, uuid.toString(), userRequest, null, null));

    assertThrows(
        FeatureNotSupportedException.class,
        () -> workspaceService.getAuthorizedAzureCloudContext(workspaceUuid, userRequest));

    final ApiAzureIpCreationParameters ipCreationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid);

    ControlledAzureIpResource ipResource =
        ControlledAzureIpResource.builder()
            .common(commonFields)
            .ipName(ipCreationParameters.getName())
            .region(ipCreationParameters.getRegion())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                ipResource, null, userRequest, ipCreationParameters));

    final ApiAzureDiskCreationParameters diskCreationParameters =
        ControlledResourceFixtures.getAzureDiskCreationParameters();

    ControlledAzureDiskResource diskResource =
        ControlledAzureDiskResource.builder()
            .common(commonFields)
            .diskName(diskCreationParameters.getName())
            .region(diskCreationParameters.getRegion())
            .size(diskCreationParameters.getSize())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                diskResource, null, userRequest, diskCreationParameters));

    final ApiAzureNetworkCreationParameters networkCreationParameters =
        ControlledResourceFixtures.getAzureNetworkCreationParameters();

    ControlledAzureNetworkResource networkResource =
        ControlledAzureNetworkResource.builder()
            .common(commonFields)
            .networkName(networkCreationParameters.getName())
            .region(networkCreationParameters.getRegion())
            .subnetName(networkCreationParameters.getSubnetName())
            .addressSpaceCidr(networkCreationParameters.getAddressSpaceCidr())
            .subnetAddressCidr(networkCreationParameters.getSubnetAddressCidr())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createControlledResourceSync(
                networkResource, null, userRequest, networkCreationParameters));

    final ApiAzureVmCreationParameters vmCreationParameters =
        ControlledResourceFixtures.getAzureVmCreationParameters();

    ControlledAzureVmResource vmResource =
        ControlledAzureVmResource.builder()
            .common(commonFields)
            .vmName(vmCreationParameters.getName())
            .vmSize(vmCreationParameters.getVmSize())
            .vmImage(AzureVmUtils.getImageData(vmCreationParameters.getVmImage()))
            .region(vmCreationParameters.getRegion())
            .ipId(ipResource.getResourceId())
            .diskId(diskResource.getResourceId())
            .networkId(networkResource.getResourceId())
            .build();

    assertThrows(
        FeatureNotSupportedException.class,
        () ->
            controlledResourceService.createAzureVm(
                vmResource, vmCreationParameters, null, null, null, userRequest));
  }
}
