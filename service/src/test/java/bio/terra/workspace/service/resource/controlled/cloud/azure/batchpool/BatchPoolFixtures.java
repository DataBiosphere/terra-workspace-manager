package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import com.azure.resourcemanager.batch.models.ApplicationPackageReference;
import com.azure.resourcemanager.batch.models.AutoScaleSettings;
import com.azure.resourcemanager.batch.models.ComputeNodeDeallocationOption;
import com.azure.resourcemanager.batch.models.DynamicVNetAssignmentScope;
import com.azure.resourcemanager.batch.models.EnvironmentSetting;
import com.azure.resourcemanager.batch.models.FixedScaleSettings;
import com.azure.resourcemanager.batch.models.InboundEndpointProtocol;
import com.azure.resourcemanager.batch.models.InboundNatPool;
import com.azure.resourcemanager.batch.models.IpAddressProvisioningType;
import com.azure.resourcemanager.batch.models.MetadataItem;
import com.azure.resourcemanager.batch.models.NetworkConfiguration;
import com.azure.resourcemanager.batch.models.NetworkSecurityGroupRule;
import com.azure.resourcemanager.batch.models.NetworkSecurityGroupRuleAccess;
import com.azure.resourcemanager.batch.models.PoolEndpointConfiguration;
import com.azure.resourcemanager.batch.models.PublicIpAddressConfiguration;
import com.azure.resourcemanager.batch.models.ResourceFile;
import com.azure.resourcemanager.batch.models.ScaleSettings;
import com.azure.resourcemanager.batch.models.StartTask;
import com.azure.resourcemanager.batch.models.TaskContainerSettings;
import com.azure.resourcemanager.batch.models.UserIdentity;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class BatchPoolFixtures {
  public static final String START_TASK_COMMAND_LINE = "/bin/sh";
  public static final Integer RETRY_COUNT = 3;
  public static final Boolean WAIT_FOR_SUCCESS = Boolean.TRUE;
  public static final String RESOURCE_GROUP = "resourceGroup";
  public static final String IDENTITY_NAME = "identity1";
  public static final UUID IDENTITY_CLIENT_ID = UUID.randomUUID();
  public static final String RESOURCE_FILE_PATH = "/opt";
  public static final String RESOURCE_FILE_MODE = "wxr";
  public static final String ENVIRONMENT_SETTINGS_NAME = "varName";
  public static final String ENVIRONMENT_SETTINGS_VALUE = "varKey";
  public static final String USER_IDENTITY_NAME = "identityName";
  public static final String IMAGE_NAME = "ubuntu_18.04";
  public static final String CONTAINER_RUN_OPTIONS = "-debug";
  public static final ComputeNodeDeallocationOption COMPUTE_NODE_DEALLOCATION_OPTION =
      ComputeNodeDeallocationOption.REQUEUE;
  public static final Duration RESIZE_TIMEOUT_MILLISECONDS = Duration.ofMillis(2000);
  public static final Integer TARGET_LOW_PRIORITY_NODES = 3;
  public static final Integer TARGET_DEDICATED_NODES = 5;
  public static final Duration EVALUATION_INTERVAL_MILLISECONDS = Duration.ofMillis(1000);
  public static final String FORMULA = "3n + 1";
  public static final String PACKAGE_ID = "packageId";
  public static final String PACKAGE_VERSION = "v1.0";
  public static final String SUBNET_ID = "subnetId";
  public static final DynamicVNetAssignmentScope DYNAMIC_VNET_ASSIGNMENT_SCOPE =
      DynamicVNetAssignmentScope.JOB;
  public static final InboundEndpointProtocol INBOUND_ENDPOINT_PROTOCOL =
      InboundEndpointProtocol.TCP;
  public static final int BACKEND_PORT = 80;
  public static final String NAT_POOL_NAME = "natPoolName";
  public static final int FRONTEND_PORT_RANGE_END = 65000;
  public static final int FRONTEND_PORT_RANGE_START = 65005;
  public static final List<String> NETWORK_SECURITY_GROUP_RULE_PORT_RANGES =
      List.of("8080", "8082");
  public static final String NETWORK_SECURITY_GROUP_RULE_PREFIX = "prefix";
  public static final NetworkSecurityGroupRuleAccess NETWORK_SECURITY_GROUP_RULE_ACCESS =
      NetworkSecurityGroupRuleAccess.ALLOW;
  public static final int NETWORK_SECURITY_GROUP_RULE_PRIORITY = 100;
  public static final IpAddressProvisioningType IP_ADDRESS_PROVISIONING_TYPE =
      IpAddressProvisioningType.NO_PUBLIC_IPADDRESSES;
  public static final List<String> IP_ADDRESS_IDS = List.of("id1", "id2");
  public static final String METADATA_NAME = "metadataName";
  public static final String METADATA_VALUE = "metadataValue";

  private BatchPoolFixtures() {}

  public static BatchPoolUserAssignedManagedIdentity createUamiWithName() {
    return new BatchPoolUserAssignedManagedIdentity(RESOURCE_GROUP, IDENTITY_NAME, null);
  }

  public static BatchPoolUserAssignedManagedIdentity createUamiWithClientId() {
    return new BatchPoolUserAssignedManagedIdentity(RESOURCE_GROUP, null, IDENTITY_CLIENT_ID);
  }

  public static StartTask createStartTask() {
    return new StartTask()
        .withCommandLine(START_TASK_COMMAND_LINE)
        .withResourceFiles(List.of(createResourceFile()))
        .withEnvironmentSettings(List.of(createEnvironmentSetting()))
        .withUserIdentity(createUserIdentity())
        .withMaxTaskRetryCount(RETRY_COUNT)
        .withWaitForSuccess(WAIT_FOR_SUCCESS)
        .withContainerSettings(createTaskContainerSettings());
  }

  public static ScaleSettings createFixedScaleSettings() {
    return new ScaleSettings()
        .withFixedScale(
            new FixedScaleSettings()
                .withNodeDeallocationOption(COMPUTE_NODE_DEALLOCATION_OPTION)
                .withResizeTimeout(RESIZE_TIMEOUT_MILLISECONDS)
                .withTargetLowPriorityNodes(TARGET_LOW_PRIORITY_NODES)
                .withTargetDedicatedNodes(TARGET_DEDICATED_NODES));
  }

  public static ScaleSettings createAutoScaleSettings() {
    return new ScaleSettings()
        .withAutoScale(
            new AutoScaleSettings()
                .withEvaluationInterval(EVALUATION_INTERVAL_MILLISECONDS)
                .withFormula(FORMULA));
  }

  public static List<ApplicationPackageReference> createApplicationPackages() {
    return List.of(
        new ApplicationPackageReference().withId(PACKAGE_ID).withVersion(PACKAGE_VERSION));
  }

  public static NetworkConfiguration createNetworkConfiguration() {
    return new NetworkConfiguration()
        .withPublicIpAddressConfiguration(createPublicIpAddressConfiguration())
        .withEndpointConfiguration(createPoolEndpointConfiguration())
        .withSubnetId(SUBNET_ID)
        .withDynamicVNetAssignmentScope(DYNAMIC_VNET_ASSIGNMENT_SCOPE);
  }

  public static List<MetadataItem> createMetadata() {
    return List.of(new MetadataItem().withName(METADATA_NAME).withValue(METADATA_VALUE));
  }

  private static PublicIpAddressConfiguration createPublicIpAddressConfiguration() {
    return new PublicIpAddressConfiguration()
        .withIpAddressIds(IP_ADDRESS_IDS)
        .withProvision(IP_ADDRESS_PROVISIONING_TYPE);
  }

  private static PoolEndpointConfiguration createPoolEndpointConfiguration() {
    return new PoolEndpointConfiguration().withInboundNatPools(List.of(createInboundNatPool()));
  }

  private static InboundNatPool createInboundNatPool() {
    return new InboundNatPool()
        .withFrontendPortRangeStart(FRONTEND_PORT_RANGE_START)
        .withFrontendPortRangeEnd(FRONTEND_PORT_RANGE_END)
        .withName(NAT_POOL_NAME)
        .withNetworkSecurityGroupRules(List.of(createNetworkSecurityGroupRule()))
        .withBackendPort(BACKEND_PORT)
        .withProtocol(INBOUND_ENDPOINT_PROTOCOL);
  }

  private static NetworkSecurityGroupRule createNetworkSecurityGroupRule() {
    return new NetworkSecurityGroupRule()
        .withSourcePortRanges(NETWORK_SECURITY_GROUP_RULE_PORT_RANGES)
        .withSourceAddressPrefix(NETWORK_SECURITY_GROUP_RULE_PREFIX)
        .withAccess(NETWORK_SECURITY_GROUP_RULE_ACCESS)
        .withPriority(NETWORK_SECURITY_GROUP_RULE_PRIORITY);
  }

  private static ResourceFile createResourceFile() {
    return new ResourceFile().withFilePath(RESOURCE_FILE_PATH).withFileMode(RESOURCE_FILE_MODE);
  }

  private static EnvironmentSetting createEnvironmentSetting() {
    return new EnvironmentSetting()
        .withName(ENVIRONMENT_SETTINGS_NAME)
        .withValue(ENVIRONMENT_SETTINGS_VALUE);
  }

  private static UserIdentity createUserIdentity() {
    return new UserIdentity().withUsername(USER_IDENTITY_NAME);
  }

  private static TaskContainerSettings createTaskContainerSettings() {
    return new TaskContainerSettings()
        .withImageName(IMAGE_NAME)
        .withContainerRunOptions(CONTAINER_RUN_OPTIONS);
  }
}
