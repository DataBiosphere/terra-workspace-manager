package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFields;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder;
import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.uniqueBucketName;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;

import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionTag;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import bio.terra.workspace.generated.model.ApiAzureVmUser;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.azure.core.management.Region;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

/** A series of static objects useful for testing controlled resources. */
public class ControlledAzureResourceFixtures {

  public static final String AZURE_NAME_PREFIX = "az";
  public static final String AZURE_DISK_NAME_PREFIX = "disk";
  public static final String AZURE_VM_NAME_PREFIX = "vm";
  public static final String DEFAULT_AZURE_RESOURCE_REGION = Region.US_EAST2.name();
  public static final String TEST_AZURE_STORAGE_ACCOUNT_NAME = "teststgacctdonotdelete";

  private ControlledAzureResourceFixtures() {}

  /** Construct a parameter object with a unique disk name to avoid unintended clashes. */
  public static ApiAzureDiskCreationParameters getAzureDiskCreationParameters() {
    return new ApiAzureDiskCreationParameters()
        .name(uniqueAzureName(AZURE_DISK_NAME_PREFIX))
        .size(50);
  }

  /** Construct a parameter object with a unique name to avoid unintended clashes. */
  public static ApiAzureStorageContainerCreationParameters
      getAzureStorageContainerCreationParameters() {
    return new ApiAzureStorageContainerCreationParameters()
        .storageContainerName(uniqueBucketName());
  }

  /** Construct a parameter object with a unique vm name to avoid unintended clashes. */
  public static ApiAzureVmCreationParameters getAzureVmCreationParameters() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .diskId(UUID.randomUUID());
  }

  public static ApiAzureVmCreationParameters
      getAzureVmCreationParametersWithCustomScriptExtension() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .diskId(UUID.randomUUID())
        .customScriptExtension(getAzureVmCustomScriptExtension());
  }

  public static ApiAzureVmCreationParameters
      getAzureVmCreationParametersWithEphemeralOsDiskAndCustomData() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .vmSize(VirtualMachineSizeTypes.STANDARD_D8S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .ephemeralOSDisk(ApiAzureVmCreationParameters.EphemeralOSDiskEnum.OS_CACHE)
        .customData(
            Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
  }

  public static ApiAzureVmCreationParameters getInvalidAzureVmCreationParameters() {
    // use password which is not strong enough
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("noname"))
        .diskId(UUID.randomUUID())
        .customScriptExtension(getAzureVmCustomScriptExtension());
  }

  public static ApiAzureVmCustomScriptExtension getAzureVmCustomScriptExtension() {
    final String[] customScriptFileUri =
        new String[] {
          "https://raw.githubusercontent.com/DataBiosphere/leonardo/TOAZ-83-dummy-script/http/src/main/resources/init-resources/msdsvmcontent/dummy_script.sh"
        };
    final String commandToExecute = "bash dummy_script.sh hello";

    var publicSettings =
        Arrays.asList(
            new ApiAzureVmCustomScriptExtensionSetting().key("fileUris").value(customScriptFileUri),
            new ApiAzureVmCustomScriptExtensionSetting()
                .key("commandToExecute")
                .value(commandToExecute));

    var tags =
        Collections.singletonList(
            new ApiAzureVmCustomScriptExtensionTag().key("tag").value("tagValue"));

    return new ApiAzureVmCustomScriptExtension()
        .name("vm-custom-script-extension")
        .publisher("Microsoft.Azure.Extensions")
        .type("CustomScript")
        .version("2.1")
        .minorVersionAutoUpgrade(true)
        .publicSettings(publicSettings)
        .tags(tags);
  }

  public static String uniqueAzureName(String resourcePrefix) {
    return TestUtils.appendRandomNumber(AZURE_NAME_PREFIX + "-" + resourcePrefix);
  }

  public static String uniqueStorageAccountName() {
    return UUID.randomUUID().toString().toLowerCase().replace("-", "").substring(0, 23);
  }

  public static ControlledAzureDiskResource getAzureDisk(String diskName, String region, int size) {
    return ControlledAzureDiskResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().region(region).build())
        .diskName(diskName)
        .size(size)
        .build();
  }

  public static ControlledAzureStorageContainerResource getAzureStorageContainer(
      String storageContainerName) {
    return ControlledAzureStorageContainerResource.builder()
        .common(makeDefaultControlledResourceFields(WORKSPACE_ID))
        .storageContainerName(storageContainerName)
        .build();
  }

  public static ControlledAzureStorageContainerResource getAzureStorageContainer(
      UUID workspaceUuid,
      UUID containerResourceId,
      String containerName,
      String resourceName,
      String resourceDescription) {
    return ControlledAzureStorageContainerResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .resourceId(containerResourceId)
                .name(resourceName)
                .description(resourceDescription)
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region("eastus") // Needs to match the AzureControlledStorageContainerFlightTest
                .build())
        .storageContainerName(containerName)
        .build();
  }

  public static ControlledAzureBatchPoolResource.Builder getAzureBatchPoolResourceBuilder(
      UUID batchPoolId,
      String batchPoolDisplayName,
      String vmSize,
      DeploymentConfiguration deploymentConfiguration,
      String resourceDescription) {
    return ControlledAzureBatchPoolResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(WORKSPACE_ID)
                .resourceId(batchPoolId)
                .name(batchPoolDisplayName)
                .description(resourceDescription)
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .build())
        .id(batchPoolId.toString())
        .displayName(batchPoolDisplayName)
        .vmSize(vmSize)
        .deploymentConfiguration(deploymentConfiguration);
  }

  public static ControlledAzureVmResource getAzureVm(
      ApiAzureVmCreationParameters creationParameters) {
    return ControlledAzureVmResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(creationParameters.getDiskId())
        .build();
  }

  public static ControlledAzureStorageContainerResource.Builder
      makeDefaultAzureStorageContainerResourceBuilder(UUID workspaceId) {
    return ControlledAzureStorageContainerResource.builder()
        .common(makeDefaultControlledResourceFields(workspaceId))
        .storageContainerName(TestUtils.appendRandomNumber("storageaccountfoo"));
  }

  public static ControlledAzureDiskResource.Builder makeDefaultAzureDiskBuilder(
      ApiAzureDiskCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureDiskResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("disk"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .build())
        .diskName(creationParameters.getName())
        .size(creationParameters.getSize());
  }

  public static ControlledAzureVmResource.Builder makeDefaultControlledAzureVmResourceBuilder(
      ApiAzureVmCreationParameters creationParameters, UUID workspaceId, UUID diskResourceId) {
    return ControlledAzureVmResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("vm"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(diskResourceId);
  }
}
