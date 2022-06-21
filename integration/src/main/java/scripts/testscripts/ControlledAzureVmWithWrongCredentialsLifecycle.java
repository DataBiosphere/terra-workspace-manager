package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.api.ControlledAzureResourceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AzureVmCreationParameters;
import bio.terra.workspace.model.AzureVmUser;
import bio.terra.workspace.model.CreateControlledAzureVmRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureNetwork;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ControlledAzureVmTestScriptBase;

public class ControlledAzureVmWithWrongCredentialsLifecycle
    extends ControlledAzureVmTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledAzureVmWithWrongCredentialsLifecycle.class);

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
    vmRequestBody.azureVm(vmCreationParameters);
    CreatedControlledAzureVmResult vmCreateResult =
        azureApi.createAzureVm(vmRequestBody, getWorkspaceId());
    assertNotNull(vmCreateResult);
    return vmCreateResult;
  }

  @Override
  protected void validateVm(CreatedControlledAzureVmResult vmCreateResult) {
    assertEquals(JobReport.StatusEnum.FAILED, vmCreateResult.getJobReport().getStatus());
    assertNull(vmCreateResult.getAzureVm());
    logger.info("VM provisioning failed.");
  }

  @Override
  protected AzureVmUser createUser() {
    String userName = "jupuser";
    String userPassword = "1234"; // simple password
    return new AzureVmUser().name(userName).password(userPassword);
  }
}
