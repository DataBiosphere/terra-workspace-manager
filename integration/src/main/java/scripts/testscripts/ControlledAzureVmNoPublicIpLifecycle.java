package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AzureVmCreationParameters;
import bio.terra.workspace.model.CreateControlledAzureVmRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureNetwork;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobControl;
import scripts.utils.ControlledAzureVmTestScriptBase;

/** Creates VM with private IP and without Custom Script Extension. */
public class ControlledAzureVmNoPublicIpLifecycle extends ControlledAzureVmTestScriptBase {
  @Override
  protected CreatedControlledAzureVmResult createVm(
      String resourceSuffix,
      String createVmJobId,
      CreatedControlledAzureDisk disk,
      CreatedControlledAzureNetwork network)
      throws ApiException {
    CreateControlledAzureVmRequestBody vmRequestBody =
        new CreateControlledAzureVmRequestBody()
            .common(createCommonFields("common-vm", resourceSuffix))
            .jobControl(new JobControl().id(createVmJobId));
    AzureVmCreationParameters vmCreationParameters =
        new AzureVmCreationParameters()
            .name(String.format("vm-%s", resourceSuffix))
            .region(REGION)
            .vmSize("Standard_D8s_v3")
            .diskId(disk.getResourceId())
            .networkId(network.getResourceId())
            .vmImage(createAzureVmImage())
            .vmUser(createUser());
    vmRequestBody.azureVm(vmCreationParameters);
    CreatedControlledAzureVmResult vmCreateResult =
        azureApi.createAzureVm(vmRequestBody, getWorkspaceId());
    assertNotNull(vmCreateResult);
    return vmCreateResult;
  }
}
