package bio.terra.workspace.common.fixtures;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;
import static bio.terra.workspace.connected.AzureConnectedTestUtils.getAzureName;

import bio.terra.stairway.ShortUUID;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.*;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.ControlledAzureBatchPoolResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.ControlledAzureNetworkResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace.ControlledAzureRelayNamespaceResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.ControlledAzureVmResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource.Builder;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes;
import com.google.api.client.util.DateTime;
import com.google.api.services.bigquery.model.Dataset;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;

/** A series of static objects useful for testing controlled resources. */
public class ControlledResourceFixtures {

  public static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  public static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  public static final UUID STORAGE_ACCOUNT_REFERENCE_ID =
      UUID.fromString("33333333-fcf0-4981-bb96-6b8dd634e7c0");
  public static final String OWNER_EMAIL = "jay@all-the-bits-thats-fit-to-blit.dev";
  public static final ApiGcpGcsBucketLifecycleRule LIFECYCLE_RULE_1 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(
                      ApiGcpGcsBucketLifecycleRuleActionType
                          .DELETE)) // no storage class required for delete actions
          .condition(
              new ApiGcpGcsBucketLifecycleRuleCondition()
                  .age(64)
                  .live(true)
                  .addMatchesStorageClassItem(ApiGcpGcsBucketDefaultStorageClass.ARCHIVE)
                  .numNewerVersions(2));

  public static final ApiGcpGcsBucketLifecycleRule LIFECYCLE_RULE_2 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS))
          .condition(
              new ApiGcpGcsBucketLifecycleRuleCondition()
                  .createdBefore(OffsetDateTime.parse("2007-01-03T00:00:00.00Z"))
                  .addMatchesStorageClassItem(ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  // list must not be immutable if deserialization is to work
  static final List<ApiGcpGcsBucketLifecycleRule> LIFECYCLE_RULES =
      new ArrayList<>(List.of(LIFECYCLE_RULE_1, LIFECYCLE_RULE_2));
  public static final String BUCKET_NAME_PREFIX = "my-bucket";
  public static final String AZURE_NAME_PREFIX = "az";
  public static final String AZURE_IP_NAME_PREFIX = "ip";
  public static final String AZURE_RELAY_NAMESPACE_NAME_PREFIX = "relay-ns";
  public static final String AZURE_DISK_NAME_PREFIX = "disk";
  public static final String AZURE_NETWORK_NAME_PREFIX = "network";
  public static final String AZURE_SUBNET_NAME_PREFIX = "subnet";
  public static final String AZURE_VM_NAME_PREFIX = "vm";
  public static final Map<String, String> DEFAULT_RESOURCE_PROPERTIES = Map.of("foo", "bar");

  public static final ApiGcpGcsBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS_MINIMAL =
      new ApiGcpGcsBucketCreationParameters()
          .name(TestUtils.appendRandomNumber(BUCKET_NAME_PREFIX))
          .location(GcpResourceConstant.DEFAULT_REGION);
  public static final String DEFAULT_AZURE_RESOURCE_REGION = "westcentralus";

  /** Construct a parameter object with a unique bucket name to avoid unintended clashes. */
  public static ApiGcpGcsBucketCreationParameters getGoogleBucketCreationParameters() {
    return new ApiGcpGcsBucketCreationParameters()
        .name(uniqueBucketName())
        .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
        .location(GcpResourceConstant.DEFAULT_REGION)
        .lifecycle(new ApiGcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));
  }

  /** Construct a creation parameter object with a unique data set name. */
  public static ApiGcpBigQueryDatasetCreationParameters getGcpBigQueryDatasetCreationParameters() {
    return new ApiGcpBigQueryDatasetCreationParameters()
        .datasetId(uniqueDatasetId())
        .defaultTableLifetime(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME)
        .defaultPartitionLifetime(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME)
        .location(DEFAULT_RESOURCE_REGION);
  }

  /** Construct a parameter object with a unique ip name to avoid unintended clashes. */
  public static ApiAzureIpCreationParameters getAzureIpCreationParameters() {
    return new ApiAzureIpCreationParameters()
        .name(uniqueAzureName(AZURE_IP_NAME_PREFIX))
        .region(DEFAULT_AZURE_RESOURCE_REGION);
  }

  /** Construct a parameter object with a unique ip name to avoid unintended clashes. */
  public static ApiAzureRelayNamespaceCreationParameters
      getAzureRelayNamespaceCreationParameters() {
    return new ApiAzureRelayNamespaceCreationParameters()
        .namespaceName(uniqueAzureName(AZURE_RELAY_NAMESPACE_NAME_PREFIX))
        .region(DEFAULT_AZURE_RESOURCE_REGION);
  }

  /** Construct a parameter object with a unique disk name to avoid unintended clashes. */
  public static ApiAzureDiskCreationParameters getAzureDiskCreationParameters() {
    return new ApiAzureDiskCreationParameters()
        .name(uniqueAzureName(AZURE_DISK_NAME_PREFIX))
        .region(DEFAULT_AZURE_RESOURCE_REGION)
        .size(50);
  }

  /** Construct a parameter object with a unique name to avoid unintended clashes. */
  public static ApiAzureStorageCreationParameters getAzureStorageCreationParameters() {
    return new ApiAzureStorageCreationParameters()
        .storageAccountName(uniqueStorageAccountName())
        .region("eastus");
  }

  /** Construct a parameter object with a unique name to avoid unintended clashes. */
  public static ApiAzureStorageContainerCreationParameters
      getAzureStorageContainerCreationParameters() {
    return new ApiAzureStorageContainerCreationParameters()
        .storageContainerName(uniqueBucketName())
        .storageAccountId(STORAGE_ACCOUNT_REFERENCE_ID);
  }

  /** Construct a parameter object with a unique bucket name to avoid unintended clashes. */
  public static ApiAzureNetworkCreationParameters getAzureNetworkCreationParameters() {
    return new ApiAzureNetworkCreationParameters()
        .name(uniqueAzureName(AZURE_NETWORK_NAME_PREFIX))
        .subnetName(uniqueAzureName(AZURE_SUBNET_NAME_PREFIX))
        .addressSpaceCidr("192.168.0.0/16")
        .subnetAddressCidr("192.168.1.0/24")
        .region("westcentralus");
  }

  /** Construct a parameter object with a unique vm name to avoid unintended clashes. */
  public static ApiAzureVmCreationParameters getAzureVmCreationParameters() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .region(DEFAULT_AZURE_RESOURCE_REGION)
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .ipId(UUID.randomUUID())
        .diskId(UUID.randomUUID())
        .networkId(UUID.randomUUID());
  }

  public static ApiAzureVmCreationParameters
      getAzureVmCreationParametersWithCustomScriptExtension() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .region("westcentralus")
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .ipId(UUID.randomUUID())
        .diskId(UUID.randomUUID())
        .networkId(UUID.randomUUID())
        .customScriptExtension(getAzureVmCustomScriptExtension());
  }

  public static ApiAzureVmCreationParameters
      getAzureVmCreationParametersWithEphemeralOsDiskAndCustomData() {
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .region("westcentralus")
        .vmSize(VirtualMachineSizeTypes.STANDARD_D8S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("StrongP@ssowrd123!!!"))
        .ipId(UUID.randomUUID())
        .networkId(UUID.randomUUID())
        .ephemeralOSDisk(ApiAzureVmCreationParameters.EphemeralOSDiskEnum.OS_CACHE)
        .customData(
            Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));
  }

  public static ApiAzureVmCreationParameters getInvalidAzureVmCreationParameters() {
    // use password which is not strong enough
    return new ApiAzureVmCreationParameters()
        .name(uniqueAzureName(AZURE_VM_NAME_PREFIX))
        .region("westcentralus")
        .vmSize(VirtualMachineSizeTypes.STANDARD_D2S_V3.toString())
        .vmImage(
            new ApiAzureVmImage()
                .publisher("microsoft-dsvm")
                .offer("ubuntu-2004")
                .sku("2004-gen2")
                .version("22.04.27"))
        .vmUser(new ApiAzureVmUser().name("noname").password("noname"))
        .ipId(UUID.randomUUID())
        .diskId(UUID.randomUUID())
        .networkId(UUID.randomUUID())
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

  public static String uniqueBucketName() {
    return TestUtils.appendRandomNumber(BUCKET_NAME_PREFIX);
  }

  public static String uniqueAzureName(String resourcePrefix) {
    return TestUtils.appendRandomNumber(AZURE_NAME_PREFIX + "-" + resourcePrefix);
  }

  public static String uniqueStorageAccountName() {
    return UUID.randomUUID().toString().toLowerCase().replace("-", "").substring(0, 23);
  }

  public static ApiGcpAiNotebookInstanceCreationParameters defaultNotebookCreationParameters() {
    return new ApiGcpAiNotebookInstanceCreationParameters()
        .instanceId(TestUtils.appendRandomNumber("default-instance-id"))
        .location("us-east1-b")
        .machineType("e2-standard-2")
        .vmImage(
            new ApiGcpAiNotebookInstanceVmImage()
                .projectId("deeplearning-platform-release")
                .imageFamily("r-latest-cpu-experimental"));
  }

  public static ApiGcpBigQueryDatasetCreationParameters defaultBigQueryDatasetCreationParameters() {
    return new ApiGcpBigQueryDatasetCreationParameters().datasetId(uniqueDatasetId());
  }

  public static ApiGcpGcsBucketCreationParameters defaultGcsBucketCreationParameters() {
    return new ApiGcpGcsBucketCreationParameters().name(uniqueBucketName());
  }

  public static ApiControlledFlexibleResourceCreationParameters
      defaultFlexResourceCreationParameters() {
    return new ApiControlledFlexibleResourceCreationParameters()
        .typeNamespace("terra")
        .type("fake-flexible-type")
        .data(null);
  }

  public static final byte[] DEFAULT_UPDATE_FLEX_DATA =
      "{\"description\":\"this is new JSON\"}".getBytes(StandardCharsets.UTF_8);

  public static ApiFlexibleResourceUpdateParameters defaultFlexUpdateParameters() {
    return new ApiFlexibleResourceUpdateParameters().data(DEFAULT_UPDATE_FLEX_DATA);
  }

  public static final String RESOURCE_NAME = "my_first_bucket";

  public static final String RESOURCE_DESCRIPTION =
      "A bucket that had beer in it, briefly. \uD83C\uDF7B";
  public static final CloningInstructions CLONING_INSTRUCTIONS = CloningInstructions.COPY_RESOURCE;
  public static final String DEFAULT_RESOURCE_REGION = "us-central1";

  public static final Long DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME = 5900L;
  public static final Long DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME = 5901L;

  public static final ControlledResourceFields DEFAULT_GCP_CONTROLLED_RESOURCE_FIELDS =
      ControlledResourceFields.builder()
          .workspaceUuid(WORKSPACE_ID)
          .resourceId(RESOURCE_ID)
          .name(RESOURCE_NAME)
          .description(RESOURCE_DESCRIPTION)
          .cloningInstructions(CloningInstructions.COPY_RESOURCE)
          .resourceLineage(null)
          .properties(Map.of())
          .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
          .createdDate(null)
          .lastUpdatedByEmail(null)
          .lastUpdatedDate(null)
          .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
          .assignedUser(null)
          .managedBy(ManagedByType.MANAGED_BY_USER)
          .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
          .applicationId(null)
          .region(DEFAULT_RESOURCE_REGION)
          .build();

  public static ControlledGcsBucketResource getBucketResource(String bucketName) {
    return ControlledGcsBucketResource.builder()
        .common(DEFAULT_GCP_CONTROLLED_RESOURCE_FIELDS)
        .bucketName(bucketName)
        .build();
  }

  public static ControlledAzureIpResource getAzureIp(String ipName, String region) {
    return ControlledAzureIpResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().region(region).build())
        .ipName(ipName)
        .build();
  }

  public static ControlledAzureRelayNamespaceResource getAzureRelayNamespace(
      String namespaceName, String region) {
    return ControlledAzureRelayNamespaceResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().region(region).build())
        .namespaceName(namespaceName)
        .build();
  }

  public static ControlledAzureDiskResource getAzureDisk(String diskName, String region, int size) {
    return ControlledAzureDiskResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().region(region).build())
        .diskName(diskName)
        .size(size)
        .build();
  }

  public static ControlledAzureNetworkResource getAzureNetwork(
      ApiAzureNetworkCreationParameters creationParameters) {
    return ControlledAzureNetworkResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .region(creationParameters.getRegion())
                .build())
        .networkName(creationParameters.getName())
        .subnetName(creationParameters.getSubnetName())
        .addressSpaceCidr(creationParameters.getAddressSpaceCidr())
        .subnetAddressCidr(creationParameters.getSubnetAddressCidr())
        .build();
  }

  public static ControlledAzureStorageResource getAzureStorage(
      String storageAccountName, String region) {
    return ControlledAzureStorageResource.builder()
        .common(makeDefaultControlledResourceFieldsBuilder().region(region).build())
        .storageAccountName(storageAccountName)
        .build();
  }

  public static ControlledAzureStorageResource getAzureStorage(
      UUID workspaceUuid,
      UUID accountResourceId,
      String storageAccountName,
      String region,
      String resourceName,
      String resourceDescription) {
    return ControlledAzureStorageResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceUuid)
                .resourceId(accountResourceId)
                .name(resourceName)
                .description(resourceDescription)
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .region(region)
                .build())
        .storageAccountName(storageAccountName)
        .build();
  }

  public static ControlledAzureStorageContainerResource getAzureStorageContainer(
      UUID storageAccountId, String storageContainerName) {
    return ControlledAzureStorageContainerResource.builder()
        .common(makeDefaultControlledResourceFields(WORKSPACE_ID))
        .storageContainerName(storageContainerName)
        .storageAccountId(storageAccountId)
        .build();
  }

  public static ControlledAzureStorageContainerResource getAzureStorageContainer(
      UUID workspaceUuid,
      UUID accountResourceId,
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
        .storageAccountId(accountResourceId)
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
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .region(creationParameters.getRegion())
                .build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
        .ipId(creationParameters.getIpId())
        .networkId(creationParameters.getNetworkId())
        .diskId(creationParameters.getDiskId())
        .build();
  }

  private ControlledResourceFixtures() {}

  /** Returns a {@link ControlledResourceFields.Builder} with the fields filled in */
  public static ControlledResourceFields.Builder makeDefaultControlledResourceFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(RandomStringUtils.randomAlphabetic(10))
        .description("how much data could a dataset set if a dataset could set data?")
        .description(RESOURCE_DESCRIPTION)
        .cloningInstructions(CloningInstructions.COPY_DEFINITION)
        .assignedUser(null)
        .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
        .properties(DEFAULT_RESOURCE_PROPERTIES)
        .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  public static ControlledAzureStorageContainerResource.Builder
      makeDefaultAzureStorageContainerResourceBuilder(UUID workspaceId) {
    return ControlledAzureStorageContainerResource.builder()
        .common(makeDefaultControlledResourceFields(workspaceId))
        .storageContainerName(TestUtils.appendRandomNumber("storageaccountfoo"))
        .storageAccountId(UUID.randomUUID());
  }

  /**
   * Returns a {@link ControlledResourceFields} that is ready to be included in a controlled
   * resource builder.
   */
  public static ControlledResourceFields makeDefaultControlledResourceFields(
      @Nullable UUID inWorkspaceId) {
    return makeControlledResourceFieldsBuilder(inWorkspaceId).build();
  }

  /**
   * Returns a {@link ControlledResourceFields.Builder} with default values. This builder can be
   * modified for particular fields before being included in a controlled resource builder.
   */
  public static ControlledResourceFields.Builder makeControlledResourceFieldsBuilder(
      @Nullable UUID inWorkspaceId) {
    ControlledResourceFields.Builder builder = makeDefaultControlledResourceFieldsBuilder();
    if (inWorkspaceId != null) {
      builder.workspaceUuid(inWorkspaceId);
    }
    return builder;
  }

  /**
   * Returns the same fields as {@link #makeDefaultControlledResourceFields(UUID)}, but in the
   * format required for a controller API call.
   */
  public static ApiControlledResourceCommonFields makeDefaultControlledResourceFieldsApi() {
    ControlledResourceFields commonFields = makeDefaultControlledResourceFieldsBuilder().build();
    return new ApiControlledResourceCommonFields()
        .name(commonFields.getName())
        .description(commonFields.getDescription())
        .cloningInstructions(commonFields.getCloningInstructions().toApiModel())
        .accessScope(commonFields.getAccessScope().toApiModel())
        .managedBy(commonFields.getManagedBy().toApiModel())
        .resourceId(commonFields.getResourceId())
        .properties(convertMapToApiProperties(commonFields.getProperties()));
  }

  /** Returns a {@link ControlledGcsBucketResource.Builder} that is ready to be built. */
  public static ControlledGcsBucketResource.Builder makeDefaultControlledGcsBucketBuilder(
      @Nullable UUID workspaceUuid) {
    return new ControlledGcsBucketResource.Builder()
        .common(makeDefaultControlledResourceFields(workspaceUuid))
        .bucketName(uniqueBucketName());
  }

  /**
   * Make a bigquery builder with defaults filled in
   *
   * @return resource builder
   */
  public static ControlledBigQueryDatasetResource.Builder makeDefaultControlledBqDatasetBuilder(
      @Nullable UUID workspaceUuid) {
    return new Builder()
        .common(makeDefaultControlledResourceFields(workspaceUuid))
        .projectId("my_project")
        .datasetName(uniqueDatasetId())
        .defaultTableLifetime(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME)
        .defaultPartitionLifetime(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME);
  }

  /** Make a flex resource builder with defaults filled in. */
  public static ControlledFlexibleResource.Builder makeDefaultFlexResourceBuilder(
      @Nullable UUID workspaceUuid) {
    return new ControlledFlexibleResource.Builder()
        .common(makeDefaultControlledResourceFields(workspaceUuid))
        .typeNamespace("terra")
        .type("fake-flexible-type")
        .data(null);
  }

  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_NEW =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(3600L)
          .defaultPartitionLifetime(3601L);
  public static final ApiGcpBigQueryDatasetUpdateParameters BQ_DATASET_UPDATE_PARAMETERS_PREV =
      new ApiGcpBigQueryDatasetUpdateParameters()
          .defaultTableLifetime(4800L)
          .defaultPartitionLifetime(4801L);
  public static final Dataset BQ_DATASET_WITH_EXPIRATION =
      new Dataset()
          .setDefaultTableExpirationMs(DEFAULT_CREATED_BIG_QUERY_TABLE_LIFETIME * 1000)
          .setDefaultPartitionExpirationMs(DEFAULT_CREATED_BIG_QUERY_PARTITION_LIFETIME * 1000);
  public static final Dataset BQ_DATASET_WITHOUT_EXPIRATION = new Dataset();

  public static String uniqueDatasetId() {
    return "my_test_dataset_" + ShortUUID.get().replace("-", "_");
  }

  /**
   * Returns a {@link ControlledAiNotebookInstanceResource.Builder} that is ready to be built.
   *
   * <p>Tests should not rely on any particular value for the fields returned by this function and
   * instead override the values that they care about.
   */
  public static ControlledResourceFields.Builder makeNotebookCommonFieldsBuilder() {
    return ControlledResourceFields.builder()
        .workspaceUuid(UUID.randomUUID())
        .resourceId(UUID.randomUUID())
        .name(TestUtils.appendRandomNumber("my-instance"))
        .description("my notebook description")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .assignedUser("myusername@mydomain.mine")
        .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
        .managedBy(ManagedByType.MANAGED_BY_USER)
        .createdByEmail(MockMvcUtils.DEFAULT_USER_EMAIL)
        .region(DEFAULT_RESOURCE_REGION);
  }

  public static ControlledAiNotebookInstanceResource.Builder makeDefaultAiNotebookInstance() {
    return ControlledAiNotebookInstanceResource.builder()
        .common(makeNotebookCommonFieldsBuilder().build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .location("us-east1-b")
        .projectId("my-project-id");
  }

  public static ControlledAiNotebookInstanceResource.Builder makeDefaultAiNotebookInstance(
      UUID workspaceId) {
    return ControlledAiNotebookInstanceResource.builder()
        .common(makeNotebookCommonFieldsBuilder().workspaceUuid(workspaceId).build())
        .instanceId(TestUtils.appendRandomNumber("my-cloud-id"))
        .location("us-east1-b")
        .projectId("my-project-id");
  }

  public static final ApiGcpAiNotebookUpdateParameters AI_NOTEBOOK_PREV_PARAMETERS =
      new ApiGcpAiNotebookUpdateParameters()
          .metadata(ImmutableMap.of("sky", "blue", "rose", "red", "foo", "bar2", "count", "0"));

  public static final ApiGcpAiNotebookUpdateParameters AI_NOTEBOOK_UPDATE_PARAMETERS =
      new ApiGcpAiNotebookUpdateParameters().metadata(ImmutableMap.of("foo", "bar", "count", "3"));

  public static final OffsetDateTime OFFSET_DATE_TIME_1 =
      OffsetDateTime.parse("2017-12-03T10:15:30+01:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  public static final OffsetDateTime OFFSET_DATE_TIME_2 =
      OffsetDateTime.parse("2017-12-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME);

  public static final DateTime DATE_TIME_1 = DateTime.parseRfc3339("1985-04-12T23:20:50.52Z");
  public static final DateTime DATE_TIME_2 = DateTime.parseRfc3339("1996-12-19T16:39:57-08:00");

  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_1 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(31)
          .createdBefore(OFFSET_DATE_TIME_2) // need date part only
          .numNewerVersions(3)
          .live(true)
          .matchesStorageClass(
              ImmutableList.of(
                  ApiGcpGcsBucketDefaultStorageClass.ARCHIVE,
                  ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_2 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(15)
          .createdBefore(OFFSET_DATE_TIME_1)
          .numNewerVersions(5)
          .live(true)
          .matchesStorageClass(
              Collections.singletonList(ApiGcpGcsBucketDefaultStorageClass.ARCHIVE));
  public static final ApiGcpGcsBucketLifecycleRuleCondition WSM_LIFECYCLE_RULE_CONDITION_3 =
      new ApiGcpGcsBucketLifecycleRuleCondition()
          .age(45)
          .createdBefore(OFFSET_DATE_TIME_2)
          .numNewerVersions(1)
          .live(true)
          .matchesStorageClass(
              Collections.singletonList(ApiGcpGcsBucketDefaultStorageClass.STANDARD));
  // leave a couple of things unspecified
  public static final LifecycleCondition GCS_LIFECYCLE_CONDITION_1 =
      LifecycleCondition.newBuilder()
          .setAge(42)
          .setIsLive(false)
          .setNumberOfNewerVersions(2)
          .setDaysSinceNoncurrentTime(5)
          .setNoncurrentTimeBefore(DATE_TIME_1)
          .setCustomTimeBefore(DATE_TIME_2)
          .setDaysSinceCustomTime(100)
          .build();
  public static final LifecycleCondition GCS_LIFECYCLE_CONDITION_2 =
      LifecycleCondition.newBuilder()
          .setAge(30)
          .setIsLive(true)
          .setCreatedBefore(DATE_TIME_2)
          .setMatchesStorageClass(ImmutableList.of(StorageClass.ARCHIVE, StorageClass.COLDLINE))
          .build();
  public static final LifecycleAction GCS_DELETE_ACTION = LifecycleAction.newDeleteAction();
  public static final LifecycleAction GCS_SET_STORAGE_CLASS_ACTION =
      LifecycleAction.newSetStorageClassAction(StorageClass.STANDARD);

  public static final LifecycleRule GCS_LIFECYCLE_RULE_1 =
      new LifecycleRule(GCS_DELETE_ACTION, GCS_LIFECYCLE_CONDITION_1);
  public static final LifecycleRule GCS_LIFECYCLE_RULE_2 =
      new LifecycleRule(GCS_SET_STORAGE_CLASS_ACTION, GCS_LIFECYCLE_CONDITION_2);
  public static final BucketInfo GCS_BUCKET_INFO_1 =
      BucketInfo.newBuilder("my-bucket")
          .setStorageClass(StorageClass.STANDARD)
          .setLifecycleRules(ImmutableList.of(GCS_LIFECYCLE_RULE_1, GCS_LIFECYCLE_RULE_2))
          .build();
  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_1 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.DELETE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_1);
  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_2 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_2);
  public static final ApiGcpGcsBucketLifecycleRule WSM_LIFECYCLE_RULE_3 =
      new ApiGcpGcsBucketLifecycleRule()
          .action(
              new ApiGcpGcsBucketLifecycleRuleAction()
                  .type(ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS)
                  .storageClass(ApiGcpGcsBucketDefaultStorageClass.COLDLINE))
          .condition(WSM_LIFECYCLE_RULE_CONDITION_3);
  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_1 =
      new ApiGcpGcsBucketUpdateParameters()
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.STANDARD)
          .lifecycle(
              new ApiGcpGcsBucketLifecycle()
                  .rules(ImmutableList.of(WSM_LIFECYCLE_RULE_1, WSM_LIFECYCLE_RULE_2)));
  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_2 =
      new ApiGcpGcsBucketUpdateParameters()
          .defaultStorageClass(ApiGcpGcsBucketDefaultStorageClass.NEARLINE)
          .lifecycle(new ApiGcpGcsBucketLifecycle().rules(ImmutableList.of(WSM_LIFECYCLE_RULE_3)));
  public static final ApiGcpGcsBucketUpdateParameters BUCKET_UPDATE_PARAMETERS_EMPTY =
      new ApiGcpGcsBucketUpdateParameters();

  public static ControlledAzureIpResource.Builder makeDefaultAzureIpResource(
      ApiAzureIpCreationParameters ipCreationParameters, UUID workspaceId) {
    return ControlledAzureIpResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("ip"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(ipCreationParameters.getRegion())
                .build())
        .ipName(ipCreationParameters.getName());
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
                .region(creationParameters.getRegion())
                .build())
        .diskName(creationParameters.getName())
        .size(creationParameters.getSize());
  }

  public static ControlledAzureNetworkResource.Builder makeDefaultAzureNetworkResourceBuilder(
      ApiAzureNetworkCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureNetworkResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("network"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(creationParameters.getRegion())
                .build())
        .networkName(creationParameters.getName())
        .subnetName(creationParameters.getSubnetName())
        .addressSpaceCidr(creationParameters.getAddressSpaceCidr())
        .subnetAddressCidr(creationParameters.getSubnetAddressCidr());
  }

  public static ControlledAzureVmResource.Builder makeDefaultControlledAzureVmResourceBuilder(
      ApiAzureVmCreationParameters creationParameters,
      UUID workspaceId,
      UUID ipResourceId,
      UUID networksResourceId,
      UUID diskResourceId) {
    return ControlledAzureVmResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("vm"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(creationParameters.getRegion())
                .build())
        .vmName(creationParameters.getName())
        .vmSize(creationParameters.getVmSize())
        .vmImage(AzureVmUtils.getImageData(creationParameters.getVmImage()))
        .ipId(ipResourceId)
        .diskId(diskResourceId)
        .networkId(networksResourceId);
  }

  public static ControlledAzureRelayNamespaceResource.Builder makeDefaultRelayNamespaceBuilder(
      ApiAzureRelayNamespaceCreationParameters creationParameters, UUID workspaceId) {
    return ControlledAzureRelayNamespaceResource.builder()
        .common(
            makeDefaultControlledResourceFieldsBuilder()
                .workspaceUuid(workspaceId)
                .name(getAzureName("relaynamespace"))
                .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                .accessScope(AccessScopeType.fromApi(ApiAccessScope.SHARED_ACCESS))
                .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
                .region(creationParameters.getRegion())
                .build())
        .namespaceName(TestUtils.appendRandomNumber("namespace"));
  }

  public static void insertControlledResourceRow(
      ResourceDao resourceDao, ControlledResource resource) {
    String fakeFlightId = UUID.randomUUID().toString();
    resourceDao.createResourceStart(resource, fakeFlightId);
    resourceDao.createResourceSuccess(resource, fakeFlightId);
  }
}
