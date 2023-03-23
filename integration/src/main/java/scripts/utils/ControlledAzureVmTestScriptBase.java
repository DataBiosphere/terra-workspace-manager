package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.AzureDiskCreationParameters;
import bio.terra.workspace.model.AzureVmCustomScriptExtension;
import bio.terra.workspace.model.AzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.model.AzureVmCustomScriptExtensionTag;
import bio.terra.workspace.model.AzureVmImage;
import bio.terra.workspace.model.AzureVmUser;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledAzureDiskRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all Azure VM-based integration tests.
 *
 * <p>Defines common user journey for VM-based integration scenarios. It provides default happy path
 * implementation for vm creation which includes provisioning of network, disk, public
 * ip(optionally). This behavior can be changed in sub-classes.</>
 */
public abstract class ControlledAzureVmTestScriptBase extends ControlledAzureTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledAzureVmTestScriptBase.class);

  protected static final int VM_CREATE_WAIT_POLL_INTERVAL_SECONDS = 5;

  protected String createVmJobId;

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    azureApi = ClientTestUtils.getControlledAzureResourceClient(testUser, server);

    // create disk
    CreatedControlledAzureDisk disk = createDisk(suffix);

    // create vm
    createVmJobId = UUID.randomUUID().toString();
    CreatedControlledAzureVmResult vmCreateResult = createVm(suffix, createVmJobId, disk);

    vmCreateResult = waitForVmCreationCompletion(createVmJobId, vmCreateResult);

    validateVm(vmCreateResult);
  }

  protected void validateVm(CreatedControlledAzureVmResult vmCreateResult) {
    assertEquals(JobReport.StatusEnum.SUCCEEDED, vmCreateResult.getJobReport().getStatus());
    assertNotNull(vmCreateResult.getAzureVm());
    logger.info(
        "Create VM name={} status is {}",
        vmCreateResult.getAzureVm().getAttributes().getVmName(),
        vmCreateResult.getJobReport().getStatus().toString());
    logger.info(
        "Created VM with following parameters Name={}, Resource Id={}",
        vmCreateResult.getAzureVm().getAttributes().getVmName(),
        vmCreateResult.getAzureVm().getMetadata().getResourceId());
  }

  protected abstract CreatedControlledAzureVmResult createVm(
      String resourceSuffix,
      String createVmJobId,
      CreatedControlledAzureDisk disk)
      throws ApiException;

  protected CreatedControlledAzureDisk createDisk(String resourceSuffix) throws ApiException {
    CreateControlledAzureDiskRequestBody diskRequestBody =
        new CreateControlledAzureDiskRequestBody()
            .common(createCommonFields("common-disk", resourceSuffix));
    AzureDiskCreationParameters diskParameters =
        new AzureDiskCreationParameters()
            .name(String.format("disk-%s", suffix))
            .size(50);
    diskRequestBody.azureDisk(diskParameters);
    CreatedControlledAzureDisk disk = azureApi.createAzureDisk(diskRequestBody, getWorkspaceId());
    assertNotNull(disk.getResourceId());
    logger.info(
        "Created Disk with following parameters Name={}, Resource Id={}",
        disk.getAzureDisk().getAttributes().getDiskName(),
        disk.getResourceId());
    return disk;
  }

  protected CreatedControlledAzureVmResult waitForVmCreationCompletion(
      String createVmJobId, CreatedControlledAzureVmResult vmCreateResult)
      throws InterruptedException, ApiException {
    while (ClientTestUtils.jobIsRunning(vmCreateResult.getJobReport())) {
      TimeUnit.SECONDS.sleep(VM_CREATE_WAIT_POLL_INTERVAL_SECONDS);
      vmCreateResult = azureApi.getCreateAzureVmResult(getWorkspaceId(), createVmJobId);
    }

    return vmCreateResult;
  }

  protected ControlledResourceCommonFields createCommonFields(String name, String suffix) {
    return new ControlledResourceCommonFields()
        .name(String.format("%s-%s", name, suffix))
        .cloningInstructions(CloningInstructionsEnum.NOTHING)
        .accessScope(AccessScope.PRIVATE_ACCESS)
        .managedBy(ManagedBy.USER);
  }

  protected AzureVmImage createAzureVmImage() {
    return new AzureVmImage()
        .publisher("microsoft-dsvm")
        .offer("ubuntu-1804")
        .sku("1804-gen2")
        .version("22.04.27");
  }

  protected AzureVmUser createUser() {
    String userName = "jupuser";
    String userPassword = "SuperStrongPassword12345!!";
    return new AzureVmUser().name(userName).password(userPassword);
  }

  protected AzureVmCustomScriptExtension createVmCustomScriptExtension() {
    String[] fileUrisValue = {
      "https://raw.githubusercontent.com/DataBiosphere/leonardo/TOAZ-83-dummy-script/http/src/main/resources/init-resources/msdsvmcontent/dummy_script.sh"
    };
    return new AzureVmCustomScriptExtension()
        .name("vm-custom-script-extension")
        .publisher("Microsoft.Azure.Extensions")
        .type("CustomScript")
        .version("2.1")
        .minorVersionAutoUpgrade(true)
        .protectedSettings(
            List.of(
                new AzureVmCustomScriptExtensionSetting().key("fileUris").value(fileUrisValue),
                new AzureVmCustomScriptExtensionSetting()
                    .key("commandToExecute")
                    .value(createCommandToExecute())))
        .tags(
            Collections.singletonList(
                new AzureVmCustomScriptExtensionTag().key("Key").value("Value")));
  }

  protected String createCommandToExecute() {
    return "bash dummy_script.sh hello";
  }
}
