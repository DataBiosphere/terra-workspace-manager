package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.batch.models.ApplicationPackageReference;
import com.azure.resourcemanager.batch.models.BatchPoolIdentity;
import com.azure.resourcemanager.batch.models.MetadataItem;
import com.azure.resourcemanager.batch.models.NetworkConfiguration;
import com.azure.resourcemanager.batch.models.Pool;
import com.azure.resourcemanager.batch.models.PoolIdentityType;
import com.azure.resourcemanager.batch.models.ScaleSettings;
import com.azure.resourcemanager.batch.models.StartTask;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class CreateAzureBatchPoolStepTest extends BaseBatchPoolTest {
  private CreateAzureBatchPoolStep createAzureBatchPoolStep;

  @Mock Pool.DefinitionStages.WithCreate mockPoolDefinitionStateCreate;

  @Captor ArgumentCaptor<BatchPoolIdentity> batchPoolIdentityArgumentCaptor;
  @Captor ArgumentCaptor<StartTask> startTaskArgumentCaptor;
  @Captor ArgumentCaptor<ScaleSettings> scaleSettingsArgumentCaptor;
  @Captor ArgumentCaptor<List<ApplicationPackageReference>> applicationPackagesArgumentCaptor;
  @Captor ArgumentCaptor<NetworkConfiguration> networkConfigurationArgumentCaptor;
  @Captor ArgumentCaptor<List<MetadataItem>> metadataArgumentCaptor;

  @BeforeEach
  public void setup() {
    setupBaseMocks();
  }

  @Test
  public void createBatchPoolWithoutUAMISuccess() throws InterruptedException {
    resource = buildDefaultResourceBuilder().build();
    initDeleteStep(resource);
    setupMocks(true);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
  }

  @Test
  public void createBatchPoolWithUAMISuccess() throws InterruptedException {
    var uami = BatchPoolFixtures.createUamiWithName();
    resource = buildDefaultResourceBuilder().build();
    initDeleteStep(resource);
    setupMocks(true);
    when(mockPoolDefinitionStateCreate.withIdentity(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withIdentity(batchPoolIdentityArgumentCaptor.capture());
    assertThat(
        batchPoolIdentityArgumentCaptor.getValue().userAssignedIdentities().size(), equalTo(1));
    assertThat(
        batchPoolIdentityArgumentCaptor.getValue().type(), equalTo(PoolIdentityType.USER_ASSIGNED));
    assertTrue(
        batchPoolIdentityArgumentCaptor
            .getValue()
            .userAssignedIdentities()
            .containsKey(
                String.format(
                    CreateAzureBatchPoolStep.USER_ASSIGNED_MANAGED_IDENTITY_REFERENCE_TEMPLATE,
                    mockAzureCloudContext.getAzureSubscriptionId(),
                    BatchPoolFixtures.RESOURCE_GROUP,
                    BatchPoolFixtures.IDENTITY_NAME)));
  }

  @Test
  public void createBatchPoolUAMICantBeFoundFailure() throws InterruptedException {
    resource = buildDefaultResourceBuilder().build();
    initDeleteStep(resource);
    setupMocks(true);
    when(mockPoolDefinitionStateCreate.withIdentity(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    // simulate previous step not putting UAMI into the context.
    when(mockWorkingMap.get(GetManagedIdentityStep.MANAGED_IDENTITY_NAME, String.class))
        .thenReturn(null);
    Assertions.assertThrows(
        RuntimeException.class, () -> createAzureBatchPoolStep.doStep(mockFlightContext));
  }

  @Test
  public void createBatchPoolWithStartTaskSuccess() throws InterruptedException {
    resource = buildDefaultResourceBuilder().startTask(BatchPoolFixtures.createStartTask()).build();
    initDeleteStep(resource);
    setupMocks(true);
    when(mockPoolDefinitionStateCreate.withStartTask(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withStartTask(startTaskArgumentCaptor.capture());

    var startTask = startTaskArgumentCaptor.getValue();
    assertThat(startTask, notNullValue());
    assertThat(startTask.commandLine(), equalTo(BatchPoolFixtures.START_TASK_COMMAND_LINE));
    assertThat(startTask.maxTaskRetryCount(), equalTo(BatchPoolFixtures.RETRY_COUNT));
    assertThat(startTask.waitForSuccess(), equalTo(BatchPoolFixtures.WAIT_FOR_SUCCESS));
    var resFiles = startTask.resourceFiles();
    assertThat(resFiles, notNullValue());
    assertThat(resFiles.size(), equalTo(1));
    assertThat(resFiles.get(0).filePath(), equalTo(BatchPoolFixtures.RESOURCE_FILE_PATH));
    assertThat(resFiles.get(0).fileMode(), equalTo(BatchPoolFixtures.RESOURCE_FILE_MODE));
    var envSettings = startTask.environmentSettings();
    assertThat(envSettings, notNullValue());
    assertThat(envSettings.size(), equalTo(1));
    assertThat(envSettings.get(0).name(), equalTo(BatchPoolFixtures.ENVIRONMENT_SETTINGS_NAME));
    assertThat(envSettings.get(0).value(), equalTo(BatchPoolFixtures.ENVIRONMENT_SETTINGS_VALUE));
    var userIdentity = startTask.userIdentity();
    assertThat(userIdentity, notNullValue());
    assertThat(userIdentity.username(), equalTo(BatchPoolFixtures.USER_IDENTITY_NAME));
    var containerSettings = startTask.containerSettings();
    assertThat(containerSettings, notNullValue());
    assertThat(containerSettings.imageName(), equalTo(BatchPoolFixtures.IMAGE_NAME));
    assertThat(
        containerSettings.containerRunOptions(), equalTo(BatchPoolFixtures.CONTAINER_RUN_OPTIONS));
  }

  @Test
  public void createBatchPoolWithFixedScaleSettingsSuccess() throws InterruptedException {
    resource =
        buildDefaultResourceBuilder()
            .scaleSettings(BatchPoolFixtures.createFixedScaleSettings())
            .build();
    initDeleteStep(resource);
    setupMocks(true);

    when(mockPoolDefinitionStateCreate.withScaleSettings(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withScaleSettings(scaleSettingsArgumentCaptor.capture());

    var scaleSettings = scaleSettingsArgumentCaptor.getValue();
    assertThat(scaleSettings, notNullValue());
    var fixedScaleOptions = scaleSettings.fixedScale();
    assertThat(fixedScaleOptions, notNullValue());
    assertThat(
        fixedScaleOptions.targetDedicatedNodes(),
        equalTo(BatchPoolFixtures.TARGET_DEDICATED_NODES));
    assertThat(
        fixedScaleOptions.nodeDeallocationOption(),
        equalTo(BatchPoolFixtures.COMPUTE_NODE_DEALLOCATION_OPTION));
    assertThat(
        fixedScaleOptions.resizeTimeout(), equalTo(BatchPoolFixtures.RESIZE_TIMEOUT_MILLISECONDS));
    assertThat(
        fixedScaleOptions.targetLowPriorityNodes(),
        equalTo(BatchPoolFixtures.TARGET_LOW_PRIORITY_NODES));
  }

  @Test
  public void createBatchPoolWithAutoScaleSettingsSuccess() throws InterruptedException {
    resource =
        buildDefaultResourceBuilder()
            .scaleSettings(BatchPoolFixtures.createAutoScaleSettings())
            .build();
    initDeleteStep(resource);
    setupMocks(true);

    when(mockPoolDefinitionStateCreate.withScaleSettings(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withScaleSettings(scaleSettingsArgumentCaptor.capture());

    var scaleSettings = scaleSettingsArgumentCaptor.getValue();
    assertThat(scaleSettings, notNullValue());
    var autoScaleOptions = scaleSettings.autoScale();
    assertThat(autoScaleOptions, notNullValue());
    assertThat(autoScaleOptions.formula(), equalTo(BatchPoolFixtures.FORMULA));
    assertThat(
        autoScaleOptions.evaluationInterval(),
        equalTo(BatchPoolFixtures.EVALUATION_INTERVAL_MILLISECONDS));
  }

  @Test
  public void createBatchPoolWithApplicationPackagesSuccess() throws InterruptedException {
    resource =
        buildDefaultResourceBuilder()
            .applicationPackages(BatchPoolFixtures.createApplicationPackages())
            .build();
    initDeleteStep(resource);
    setupMocks(true);

    when(mockPoolDefinitionStateCreate.withApplicationPackages(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withApplicationPackages(applicationPackagesArgumentCaptor.capture());

    var applicationPackages = applicationPackagesArgumentCaptor.getValue();
    assertThat(applicationPackages, notNullValue());
    assertThat(applicationPackages.size(), equalTo(1));
    assertThat(applicationPackages.get(0).id(), equalTo(BatchPoolFixtures.PACKAGE_ID));
    assertThat(applicationPackages.get(0).version(), equalTo(BatchPoolFixtures.PACKAGE_VERSION));
  }

  @Test
  public void createBatchPoolWithNetworkConfigurationSuccess() throws InterruptedException {
    resource =
        buildDefaultResourceBuilder()
            .networkConfiguration(BatchPoolFixtures.createNetworkConfiguration())
            .build();
    initDeleteStep(resource);
    setupMocks(true);

    when(mockPoolDefinitionStateCreate.withNetworkConfiguration(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1))
        .withNetworkConfiguration(networkConfigurationArgumentCaptor.capture());

    var networkConfiguration = networkConfigurationArgumentCaptor.getValue();
    assertThat(networkConfiguration, notNullValue());
    assertThat(networkConfiguration.subnetId(), equalTo(BatchPoolFixtures.SUBNET_ID));
    assertThat(
        networkConfiguration.dynamicVNetAssignmentScope(),
        equalTo(BatchPoolFixtures.DYNAMIC_VNET_ASSIGNMENT_SCOPE));
    var publicIpAddressConfiguration = networkConfiguration.publicIpAddressConfiguration();
    assertThat(publicIpAddressConfiguration, notNullValue());
    assertThat(
        publicIpAddressConfiguration.provision(),
        equalTo(BatchPoolFixtures.IP_ADDRESS_PROVISIONING_TYPE));
    assertThat(publicIpAddressConfiguration.ipAddressIds().size(), equalTo(2));
    assertTrue(
        publicIpAddressConfiguration
            .ipAddressIds()
            .contains(BatchPoolFixtures.IP_ADDRESS_IDS.get(0)));
    assertTrue(
        publicIpAddressConfiguration
            .ipAddressIds()
            .contains(BatchPoolFixtures.IP_ADDRESS_IDS.get(1)));
    var poolEndpointConfiguration = networkConfiguration.endpointConfiguration();
    assertThat(poolEndpointConfiguration, notNullValue());
    assertThat(poolEndpointConfiguration.inboundNatPools().size(), equalTo(1));
    var inboundNatPool = poolEndpointConfiguration.inboundNatPools().get(0);
    assertThat(inboundNatPool.backendPort(), equalTo(BatchPoolFixtures.BACKEND_PORT));
    assertThat(inboundNatPool.name(), equalTo(BatchPoolFixtures.NAT_POOL_NAME));
    assertThat(
        inboundNatPool.frontendPortRangeEnd(), equalTo(BatchPoolFixtures.FRONTEND_PORT_RANGE_END));
    assertThat(
        inboundNatPool.frontendPortRangeStart(),
        equalTo(BatchPoolFixtures.FRONTEND_PORT_RANGE_START));
    assertThat(inboundNatPool.protocol(), equalTo(BatchPoolFixtures.INBOUND_ENDPOINT_PROTOCOL));
    assertThat(inboundNatPool.networkSecurityGroupRules().size(), equalTo(1));
    var networkSecurityGroupRule = inboundNatPool.networkSecurityGroupRules().get(0);
    assertThat(
        networkSecurityGroupRule.access(),
        equalTo(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_ACCESS));
    assertThat(
        networkSecurityGroupRule.priority(),
        equalTo(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_PRIORITY));
    assertThat(
        networkSecurityGroupRule.sourceAddressPrefix(),
        equalTo(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_PREFIX));
    assertThat(
        networkSecurityGroupRule.sourcePortRanges().size(),
        equalTo(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_PORT_RANGES.size()));
    assertTrue(
        networkSecurityGroupRule
            .sourcePortRanges()
            .contains(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_PORT_RANGES.get(0)));
    assertTrue(
        networkSecurityGroupRule
            .sourcePortRanges()
            .contains(BatchPoolFixtures.NETWORK_SECURITY_GROUP_RULE_PORT_RANGES.get(1)));
  }

  @Test
  public void createBatchPoolControlledByLzBatchAccountFailure_ShareBatchAccountNameNotSet()
      throws InterruptedException {
    resource = buildDefaultResourceBuilder().build();
    initDeleteStep(resource);
    setupMocks(false);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    verify(mockPoolDefinitionStateCreate, never()).create(any());
  }

  @Test
  public void createBatchPoolWithMetadataSuccess() throws InterruptedException {
    resource = buildDefaultResourceBuilder().metadata(BatchPoolFixtures.createMetadata()).build();
    initDeleteStep(resource);
    setupMocks(true);

    when(mockPoolDefinitionStateCreate.withMetadata(any()))
        .thenReturn(mockPoolDefinitionStateCreate);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
    verify(mockPoolDefinitionStateCreate, times(1)).withMetadata(metadataArgumentCaptor.capture());

    var metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata, notNullValue());
    assertThat(metadata.size(), equalTo(1));
    assertThat(metadata.get(0).name(), equalTo(BatchPoolFixtures.METADATA_NAME));
    assertThat(metadata.get(0).value(), equalTo(BatchPoolFixtures.METADATA_VALUE));
  }

  @Test
  public void createBatchPoolWithVmSizeNotInSDKListSucceeds() throws InterruptedException {
    resource = buildDefaultResourceBuilder().vmSize("Standard_D4ads_v5").build();
    initDeleteStep(resource);
    setupMocks(true);

    StepResult stepResult = createAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockPoolDefinitionStateCreate, times(1)).create(any());
  }

  private void initDeleteStep(ControlledAzureBatchPoolResource resource) {
    createAzureBatchPoolStep =
        new CreateAzureBatchPoolStep(mockAzureConfig, mockCrlService, resource);
  }

  private void setupMocks(boolean setBatchAccountName) {
    String batchAccountName = setBatchAccountName ? BATCH_ACCOUNT_NAME : null;
    when(mockWorkingMap.get(
            eq(WorkspaceFlightMapKeys.ControlledResourceKeys.BATCH_ACCOUNT_NAME), eq(String.class)))
        .thenReturn(batchAccountName);

    Pool.DefinitionStages.Blank mockPoolDefinitionStage = mock(Pool.DefinitionStages.Blank.class);
    when(mockPoolDefinitionStateCreate.withDeploymentConfiguration(any()))
        .thenReturn(mockPoolDefinitionStateCreate);
    when(mockPoolDefinitionStateCreate.withVmSize(anyString()))
        .thenReturn(mockPoolDefinitionStateCreate);
    when(mockPoolDefinitionStateCreate.withDisplayName(anyString()))
        .thenReturn(mockPoolDefinitionStateCreate);

    Pool mockPool = mock(Pool.class);
    when(mockPoolDefinitionStateCreate.create()).thenReturn(mockPool);
    when(mockPoolDefinitionStage.withExistingBatchAccount(anyString(), anyString()))
        .thenReturn(mockPoolDefinitionStateCreate);
    when(mockPools.define(anyString())).thenReturn(mockPoolDefinitionStage);
    when(mockBatchManager.pools()).thenReturn(mockPools);
    when(mockCrlService.getBatchManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockBatchManager);
    when(mockCrlService.getMsiManager(any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockMsiManager);
  }
}
