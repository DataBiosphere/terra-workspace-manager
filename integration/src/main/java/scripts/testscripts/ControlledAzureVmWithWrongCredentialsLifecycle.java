package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AzureVmCreationParameters;
import bio.terra.workspace.model.AzureVmUser;
import bio.terra.workspace.model.CreateControlledAzureVmRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
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

  private static final String AZURE_VM_PROVISION_ERROR_MESSAGE =
      "The supplied password must be between 6-72 characters long and must satisfy at least 3 of password complexity requirements";

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
    assertNotNull(vmCreateResult.getErrorReport());
    assertTrue(
        vmCreateResult.getErrorReport().getMessage().contains(AZURE_VM_PROVISION_ERROR_MESSAGE));
    assertTrue(
        vmCreateResult.getErrorReport().getMessage().contains("\"code\": \"InvalidParameter\""));
    assertTrue(
        vmCreateResult.getErrorReport().getMessage().contains("\"target\": \"adminPassword\""));
    assertEquals(500, vmCreateResult.getErrorReport().getStatusCode());
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
