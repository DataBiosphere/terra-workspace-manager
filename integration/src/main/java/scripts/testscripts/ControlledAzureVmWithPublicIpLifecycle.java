package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledAzureResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AzureVmCreationParameters;
import bio.terra.workspace.model.CreateControlledAzureVmRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureNetwork;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobControl;
import java.util.List;
import scripts.utils.ControlledAzureVmTestScriptBase;

/** Create Vm with public IP and without Custom Script Extension. */
public class ControlledAzureVmWithPublicIpLifecycle extends ControlledAzureVmTestScriptBase {
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    vmWithPublicIp = true;
  }

  @Override
  protected CreatedControlledAzureVmResult createVm(
      ControlledAzureResourceApi azureApi,
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

    assertTrue(publicIp.isPresent(), "Public IP should be provisioned first.");
    vmCreationParameters.ipId(publicIp.get().getResourceId());

    vmRequestBody.azureVm(vmCreationParameters);
    CreatedControlledAzureVmResult vmCreateResult =
        azureApi.createAzureVm(vmRequestBody, getWorkspaceId());
    assertNotNull(vmCreateResult);
    return vmCreateResult;
  }
}
