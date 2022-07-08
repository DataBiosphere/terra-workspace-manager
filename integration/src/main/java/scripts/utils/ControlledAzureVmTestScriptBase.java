package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.AzureDiskCreationParameters;
import bio.terra.workspace.model.AzureIpCreationParameters;
import bio.terra.workspace.model.AzureNetworkCreationParameters;
import bio.terra.workspace.model.AzureVmCustomScriptExtension;
import bio.terra.workspace.model.AzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.model.AzureVmCustomScriptExtensionTag;
import bio.terra.workspace.model.AzureVmImage;
import bio.terra.workspace.model.AzureVmUser;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledAzureDiskRequestBody;
import bio.terra.workspace.model.CreateControlledAzureIpRequestBody;
import bio.terra.workspace.model.CreateControlledAzureNetworkRequestBody;
import bio.terra.workspace.model.CreatedControlledAzureDisk;
import bio.terra.workspace.model.CreatedControlledAzureIp;
import bio.terra.workspace.model.CreatedControlledAzureNetwork;
import bio.terra.workspace.model.CreatedControlledAzureVmResult;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
  // controls if we want to provision VM with public ip
  protected boolean vmWithPublicIp;
  protected Optional<CreatedControlledAzureIp> publicIp;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    vmWithPublicIp = false;
    publicIp = Optional.empty();
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    azureApi = ClientTestUtils.getControlledAzureResourceClient(testUser, server);

    // create public ip
    if (vmWithPublicIp) {
      CreatedControlledAzureIp ip = createPublicIp(suffix);
      publicIp = Optional.of(ip);
    }

    // create network
    CreatedControlledAzureNetwork network = createNetwork(suffix);

    // create disk
    CreatedControlledAzureDisk disk = createDisk(suffix);

    // create vm
    createVmJobId = UUID.randomUUID().toString();
    CreatedControlledAzureVmResult vmCreateResult = createVm(suffix, createVmJobId, disk, network);

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
      CreatedControlledAzureDisk disk,
      CreatedControlledAzureNetwork network)
      throws ApiException;

  protected CreatedControlledAzureIp createPublicIp(String resourceSuffix) throws ApiException {
    AzureIpCreationParameters ipCreationParameters =
        new AzureIpCreationParameters().name(String.format("ip-%s", suffix)).region(REGION);
    CreateControlledAzureIpRequestBody ipRequestBody =
        new CreateControlledAzureIpRequestBody()
            .common(createCommonFields("common-ip", resourceSuffix))
            .azureIp(ipCreationParameters);
    CreatedControlledAzureIp ip = azureApi.createAzureIp(ipRequestBody, getWorkspaceId());
    assertNotNull(ip);
    logger.info(
        "Created IP with the following parameters Name={}, Resource Id={}",
        ip.getAzureIp().getAttributes().getIpName(),
        ip.getResourceId());
    return ip;
  }

  protected CreatedControlledAzureNetwork createNetwork(String resourceSuffix) throws ApiException {
    CreateControlledAzureNetworkRequestBody networkRequestBody =
        new CreateControlledAzureNetworkRequestBody()
            .common(createCommonFields("common-network", resourceSuffix));
    AzureNetworkCreationParameters networkParameters =
        new AzureNetworkCreationParameters()
            .name(String.format("network-%s", suffix))
            .subnetName(String.format("subnet-%s", suffix))
            .addressSpaceCidr("192.168.0.0/16")
            .subnetAddressCidr("192.168.1.0/24")
            .region(REGION);
    networkRequestBody.azureNetwork(networkParameters);
    CreatedControlledAzureNetwork network =
        azureApi.createAzureNetwork(networkRequestBody, getWorkspaceId());
    assertNotNull(network.getResourceId());
    logger.info(
        "Created Network with following parameters Name={}, Resource Id={}",
        network.getAzureNetwork().getAttributes().getNetworkName(),
        network.getResourceId());
    return network;
  }

  protected CreatedControlledAzureDisk createDisk(String resourceSuffix) throws ApiException {
    CreateControlledAzureDiskRequestBody diskRequestBody =
        new CreateControlledAzureDiskRequestBody()
            .common(createCommonFields("common-disk", resourceSuffix));
    AzureDiskCreationParameters diskParameters =
        new AzureDiskCreationParameters()
            .name(String.format("disk-%s", suffix))
            .size(50)
            .region(REGION);
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
