package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_ID;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;

import bio.terra.workspace.common.utils.AzureUtils;
import bio.terra.workspace.common.utils.MapperUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolDeploymentConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineImageReference;
import bio.terra.workspace.generated.model.ApiAzureDatabaseCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureDiskCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureKubernetesNamespaceCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureManagedIdentityCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCreationParameters;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtension;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionTag;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import bio.terra.workspace.generated.model.ApiAzureVmUser;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace.ControlledAzureKubernetesNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.azure.core.management.Region;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.ImageReference;
import com.azure.resourcemanager.batch.models.VirtualMachineConfiguration;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A series of static objects useful for testing controlled resources. */
public class ControlledAzureResourceFixtures {

  public static final String AZURE_NAME_PREFIX = "az";
  public static final String DEFAULT_AZURE_RESOURCE_REGION = Region.US_EAST2.name();
  private static final String AZURE_UAMI_NAME_PREFIX = "uami";
  private static final String AZURE_DATABASE_NAME_PREFIX = "db";
  private static final String AZURE_KUBERNETES_NAMESPACE_PREFIX = "k8s";

  public static String uniqueAzureName(String resourcePrefix) {
    return TestUtils.appendRandomNumber(AZURE_NAME_PREFIX + "-" + resourcePrefix);
  }

  // Azure Disk

  public static final String AZURE_DISK_NAME_PREFIX = "disk";

  /** Construct a parameter object with a unique disk name to avoid unintended clashes. */
  public static ApiAzureDiskCreationParameters getAzureDiskCreationParameters() {
    return new ApiAzureDiskCreationParameters()
        .name(uniqueAzureName(AZURE_DISK_NAME_PREFIX))
        .size(50);
  }

  public static ControlledAzureDiskResource.Builder makeDefaultAzureDiskBuilder(
      ApiAzureDiskCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureDiskResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
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

  public static ControlledAzureDiskResource getAzureDisk(String diskName, String region, int size) {
    return ControlledAzureDiskResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .region(region)
                .build())
        .diskName(diskName)
        .size(size)
        .build();
  }

  // Azure Storage

  public static final String TEST_AZURE_STORAGE_ACCOUNT_NAME = "teststgacctdonotdelete";
  public static final String STORAGE_CONTAINER_PREFIX = "my-container";

  public static String uniqueStorageAccountName() {
    return UUID.randomUUID().toString().toLowerCase().replace("-", "").substring(0, 23);
  }

  public static String uniqueStorageContainerName() {
    return TestUtils.appendRandomNumber(STORAGE_CONTAINER_PREFIX);
  }

  /** Construct a parameter object with a unique name to avoid unintended clashes. */
  public static ApiAzureStorageContainerCreationParameters
      getAzureStorageContainerCreationParameters() {
    return new ApiAzureStorageContainerCreationParameters()
        .storageContainerName(uniqueStorageContainerName());
  }

  public static ControlledAzureStorageContainerResource.Builder
      makeDefaultAzureStorageContainerResourceBuilder(UUID workspaceId) {
    return ControlledAzureStorageContainerResource.builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId))
        .storageContainerName(TestUtils.appendRandomNumber("storageaccountfoo"));
  }

  public static ControlledAzureStorageContainerResource getAzureStorageContainer(
      String storageContainerName) {
    return ControlledAzureStorageContainerResource.builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(WORKSPACE_ID))
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
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .resourceId(containerResourceId)
                .name(resourceName)
                .description(resourceDescription)
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                .managedBy(ManagedByType.MANAGED_BY_USER)
                .region("eastus") // Needs to match the AzureControlledStorageContainerFlightTest
                .build())
        .storageContainerName(containerName)
        .build();
  }

  // Azure VM

  public static final String AZURE_VM_NAME_PREFIX = "vm";

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
    String[] customScriptFileUri =
        new String[] {
          "https://raw.githubusercontent.com/DataBiosphere/leonardo/TOAZ-83-dummy-script/http/src/main/resources/init-resources/msdsvmcontent/dummy_script.sh"
        };
    String commandToExecute = "bash dummy_script.sh hello";

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

  public static ControlledAzureVmResource makeAzureVm(UUID workspaceUuid) {
    ApiAzureVmCreationParameters creationParameters = getAzureVmCreationParameters();
    return ControlledAzureVmResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(creationParameters.getDiskId())
        .build();
  }

  public static ControlledAzureVmResource getAzureVm(
      ApiAzureVmCreationParameters creationParameters) {
    return ControlledAzureVmResource.builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder().build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureUtils.getVmImageData(creationParameters.getVmImage()))
        .diskId(creationParameters.getDiskId())
        .build();
  }

  public static ControlledAzureVmResource.Builder makeDefaultControlledAzureVmResourceBuilder(
      ApiAzureVmCreationParameters creationParameters, UUID workspaceId, UUID diskResourceId) {
    return ControlledAzureVmResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
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

  // Azure Batch Pool

  private static final String BATCH_POOL_ID = UUID.randomUUID().toString();
  private static final String BATCH_POOL_VM_SIZE = "Standard_D2s_v3";
  private static final String NODE_AGENT_SKU_ID = "batch.node.ubuntu 18.04";
  private static final String IMAGE_REFERENCE_PUBLISHER = "canonical";
  private static final String IMAGE_REFERENCE_OFFER = "ubuntuserver";
  private static final String IMAGE_REFERENCE_SKU = "18.04-lts";

  public static ApiAzureBatchPoolCreationParameters createAzureBatchPoolWithRequiredParameters() {
    var imageReference =
        new ApiAzureBatchPoolVirtualMachineImageReference()
            .offer(IMAGE_REFERENCE_OFFER)
            .publisher(IMAGE_REFERENCE_PUBLISHER)
            .sku(IMAGE_REFERENCE_SKU);
    var virtualMachineConfiguration =
        new ApiAzureBatchPoolVirtualMachineConfiguration()
            .imageReference(imageReference)
            .nodeAgentSkuId(NODE_AGENT_SKU_ID);
    var deploymentConfiguration =
        new ApiAzureBatchPoolDeploymentConfiguration()
            .virtualMachineConfiguration(virtualMachineConfiguration);
    return new ApiAzureBatchPoolCreationParameters()
        .id(BATCH_POOL_ID)
        .vmSize(BATCH_POOL_VM_SIZE)
        .deploymentConfiguration(deploymentConfiguration);
  }

  public static ControlledAzureBatchPoolResource.Builder getAzureBatchPoolResourceBuilder(
      UUID batchPoolId,
      String batchPoolDisplayName,
      String vmSize,
      DeploymentConfiguration deploymentConfiguration,
      String resourceDescription) {
    return ControlledAzureBatchPoolResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
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

  public static ControlledAzureBatchPoolResource createAzureBatchPoolResource(
      ApiAzureBatchPoolCreationParameters creationParameters,
      ControlledResourceFields commonFields) {
    return ControlledAzureBatchPoolResource.builder()
        .id(creationParameters.getId())
        .vmSize(creationParameters.getVmSize())
        .deploymentConfiguration(
            MapperUtils.BatchPoolMapper.mapFrom(creationParameters.getDeploymentConfiguration()))
        .common(commonFields)
        .build();
  }

  public static ControlledAzureBatchPoolResource makeDefaultAzureBatchPoolResource(
      UUID workspaceUuid) {
    UUID resourceId = UUID.randomUUID();
    return ControlledAzureBatchPoolResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .resourceId(resourceId)
                .name("displayName")
                .description("description")
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .build())
        .id(resourceId.toString())
        .displayName("displayName2")
        .vmSize("Standard_D2s_v3")
        .deploymentConfiguration(
            new DeploymentConfiguration()
                .withVirtualMachineConfiguration(
                    new VirtualMachineConfiguration()
                        .withImageReference(
                            new ImageReference()
                                .withOffer("ubuntuserver")
                                .withSku("18.04-lts")
                                .withPublisher("canonical"))))
        .build();
  }

  public static ApiAzureManagedIdentityCreationParameters
      getAzureManagedIdentityCreationParameters() {
    return new ApiAzureManagedIdentityCreationParameters()
        .name(uniqueAzureName(AZURE_UAMI_NAME_PREFIX));
  }

  public static ControlledAzureManagedIdentityResource.Builder
      makeDefaultControlledAzureManagedIdentityResourceBuilder(
          ApiAzureManagedIdentityCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureManagedIdentityResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("uami"))
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .build())
        .managedIdentityName(creationParameters.getName());
  }

  public static ApiAzureKubernetesNamespaceCreationParameters
      getAzureKubernetesNamespaceCreationParameters(String owner, List<String> databases) {
    return new ApiAzureKubernetesNamespaceCreationParameters()
        .managedIdentity(Objects.toString(owner, null))
        .databases(databases.stream().toList())
        .namespacePrefix(uniqueAzureName(AZURE_KUBERNETES_NAMESPACE_PREFIX).substring(0, 24));
  }

  public static ControlledAzureKubernetesNamespaceResource.Builder
      makeSharedControlledAzureKubernetesNamespaceResourceBuilder(
          ApiAzureKubernetesNamespaceCreationParameters creationParameters, UUID workspaceId) {
    var namespace = creationParameters.getNamespacePrefix() + "-" + workspaceId.toString();
    return ControlledAzureKubernetesNamespaceResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("k8s"))
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .build())
        .kubernetesServiceAccount(namespace + "-ksa")
        .kubernetesNamespace(namespace)
        .managedIdentity(creationParameters.getManagedIdentity())
        .databases(new HashSet<>(creationParameters.getDatabases()));
  }

  public static ControlledAzureKubernetesNamespaceResource.Builder
      makePrivateControlledAzureKubernetesNamespaceResourceBuilder(
          ApiAzureKubernetesNamespaceCreationParameters creationParameters,
          UUID workspaceId,
          String assignedUser,
          PrivateResourceState privateResourceState) {
    var namespace = creationParameters.getNamespacePrefix() + "-" + workspaceId.toString();
    return ControlledAzureKubernetesNamespaceResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("k8s"))
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .assignedUser(assignedUser)
                .iamRole(ControlledResourceIamRole.EDITOR)
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .privateResourceState(privateResourceState)
                .build())
        .kubernetesServiceAccount(creationParameters.getNamespacePrefix() + "-ksa")
        .kubernetesNamespace(namespace)
        .databases(new HashSet<>(creationParameters.getDatabases()));
  }

  public static ApiAzureDatabaseCreationParameters getAzureDatabaseCreationParameters(
      String owner, boolean allowAccessForAllWorkspaceUsers) {
    return new ApiAzureDatabaseCreationParameters()
        .name(uniqueAzureName(AZURE_DATABASE_NAME_PREFIX))
        .owner(Objects.toString(owner, null))
        .allowAccessForAllWorkspaceUsers(allowAccessForAllWorkspaceUsers);
  }

  public static ControlledAzureDatabaseResource.Builder
      makeSharedControlledAzureDatabaseResourceBuilder(
          ApiAzureDatabaseCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureDatabaseResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("db"))
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .build())
        .databaseName(creationParameters.getName())
        .databaseOwner(creationParameters.getOwner())
        .allowAccessForAllWorkspaceUsers(creationParameters.isAllowAccessForAllWorkspaceUsers());
  }

  public static ControlledAzureDatabaseResource.Builder
      makePrivateControlledAzureDatabaseResourceBuilder(
          ApiAzureDatabaseCreationParameters creationParameters,
          UUID workspaceId,
          String assignedUser) {
    return ControlledAzureDatabaseResource.builder()
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("db"))
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .assignedUser(assignedUser)
                .iamRole(ControlledResourceIamRole.EDITOR)
                .region(DEFAULT_AZURE_RESOURCE_REGION)
                .build())
        .databaseName(creationParameters.getName());
  }
}
