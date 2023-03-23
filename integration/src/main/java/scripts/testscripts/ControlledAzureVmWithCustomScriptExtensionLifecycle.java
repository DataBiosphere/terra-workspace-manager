package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AzureVmCreationParameters;
import bio.terra.workspace.model.CreateControlledAzureVmRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobControl;
import scripts.utils.ControlledAzureVmTestScriptBase;

/** Create Vm with private IP and with Custom Script Extension. Default scenario. */
public class ControlledAzureVmWithCustomScriptExtensionLifecycle
    extends ControlledAzureVmTestScriptBase {
  @Override
  protected CreatedControlledAzureVmResult createVm(
      String resourceSuffix,
      String createVmJobId,
      CreatedControlledAzureDisk disk)
      throws ApiException {
    CreateControlledAzureVmRequestBody vmRequestBody =
        new CreateControlledAzureVmRequestBody()
            .common(createCommonFields("common-vm", resourceSuffix))
            .jobControl(new JobControl().id(createVmJobId));
    AzureVmCreationParameters vmCreationParameters =
        new AzureVmCreationParameters()
            .name(String.format("vm-%s", resourceSuffix))
            .vmSize("Standard_D8s_v3")
            .diskId(disk.getResourceId())
            .vmImage(createAzureVmImage())
            .customScriptExtension(createVmCustomScriptExtension())
            .vmUser(createUser());
    vmRequestBody.azureVm(vmCreationParameters);
    CreatedControlledAzureVmResult vmCreateResult =
        azureApi.createAzureVm(vmRequestBody, getWorkspaceId());
    assertNotNull(vmCreateResult);
    return vmCreateResult;
  }
}
