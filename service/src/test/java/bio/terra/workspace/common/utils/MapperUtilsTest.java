package bio.terra.workspace.common.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolApplicationPackageReference;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolAutoScaleSettings;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolAutoUserScope;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolAutoUserSpecification;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolComputeNodeDeallocationOption;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolComputeNodeIdentityReference;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolContainerRegistry;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolContainerWorkingDirectory;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolDeploymentConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolElevationLevel;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolEnvironmentSetting;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolFixedScaleSettings;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolMetadataItem;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolNetworkConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolResourceFile;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolScaleSettings;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolStartTask;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolTaskContainerSettings;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolUserAssignedIdentity;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolUserIdentity;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolVirtualMachineImageReference;
import bio.terra.workspace.generated.model.ApiDynamicVNetAssignmentScope;
import bio.terra.workspace.generated.model.ApiInboundEndpointProtocol;
import bio.terra.workspace.generated.model.ApiInboundNatPool;
import bio.terra.workspace.generated.model.ApiIpAddressProvisioningType;
import bio.terra.workspace.generated.model.ApiNetworkSecurityGroupRule;
import bio.terra.workspace.generated.model.ApiNetworkSecurityGroupRuleAccess;
import bio.terra.workspace.generated.model.ApiPoolEndpointConfiguration;
import bio.terra.workspace.generated.model.ApiPublicIpAddressConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MapperUtilsTest extends BaseSpringBootUnitTest {

  @Test
  public void testNullableListOfUserAssignedIdentities() {
    assertNull(MapperUtils.BatchPoolMapper.mapListOfUserAssignedIdentities(null));
  }

  @Test
  public void testEmptyListOfUserAssignedIdentities() {
    assertNull(MapperUtils.BatchPoolMapper.mapListOfUserAssignedIdentities(new ArrayList<>()));
  }

  @Test
  public void testListOfUserAssignedIdentities() {
    var identities =
        List.of(
            new ApiAzureBatchPoolUserAssignedIdentity()
                .name("test")
                .resourceGroupName("resGroupName"));

    var result = MapperUtils.BatchPoolMapper.mapListOfUserAssignedIdentities(identities);

    assertThat(result.size(), equalTo(identities.size()));
    assertThat(result.get(0).name(), equalTo(identities.get(0).getName()));
    assertThat(
        result.get(0).resourceGroupName(), equalTo(identities.get(0).getResourceGroupName()));
  }

  @Test
  public void testNullableDeploymentConfiguration() {
    assertNull(
        MapperUtils.BatchPoolMapper.mapFrom((ApiAzureBatchPoolDeploymentConfiguration) null));
  }

  @Test
  public void testDeploymentConfiguration() {
    var imageReference =
        new ApiAzureBatchPoolVirtualMachineImageReference()
            .offer("ubuntuserver")
            .sku("18.04-lts")
            .publisher("canonical");
    var virtualMachineConfiguration =
        new ApiAzureBatchPoolVirtualMachineConfiguration()
            .nodeAgentSkuId("batch.node.ubuntu 18.04")
            .imageReference(imageReference);
    var configuration =
        new ApiAzureBatchPoolDeploymentConfiguration()
            .virtualMachineConfiguration(virtualMachineConfiguration);

    var result = MapperUtils.BatchPoolMapper.mapFrom(configuration);

    assertNotNull(result);
    assertThat(
        result.virtualMachineConfiguration().nodeAgentSkuId(),
        equalTo(virtualMachineConfiguration.getNodeAgentSkuId()));
    assertThat(
        result.virtualMachineConfiguration().imageReference().offer(),
        equalTo(virtualMachineConfiguration.getImageReference().getOffer()));
    assertThat(
        result.virtualMachineConfiguration().imageReference().sku(),
        equalTo(virtualMachineConfiguration.getImageReference().getSku()));
    assertThat(
        result.virtualMachineConfiguration().imageReference().publisher(),
        equalTo(virtualMachineConfiguration.getImageReference().getPublisher()));
  }

  @Test
  public void testNullableScaleSettings() {
    assertNull(MapperUtils.BatchPoolMapper.mapFrom((ApiAzureBatchPoolScaleSettings) null));
  }

  @Test
  public void testFixedScaleSettings() {
    var fixedScale =
        new ApiAzureBatchPoolFixedScaleSettings()
            .nodeDeallocationOption(ApiAzureBatchPoolComputeNodeDeallocationOption.REQUEUE)
            .resizeTimeout(5) /* 5 minutes min.*/
            .targetDedicatedNodes(3)
            .targetLowPriorityNodes(3);
    var scaleSettings = new ApiAzureBatchPoolScaleSettings().fixedScale(fixedScale);

    var result = MapperUtils.BatchPoolMapper.mapFrom(scaleSettings);

    assertNotNull(result);
    assertThat(
        result.fixedScale().nodeDeallocationOption().toString(),
        equalTo(scaleSettings.getFixedScale().getNodeDeallocationOption().toString()));
    assertThat(
        result.fixedScale().resizeTimeout().toMinutes(),
        equalTo(scaleSettings.getFixedScale().getResizeTimeout().longValue()));
    assertThat(
        result.fixedScale().targetDedicatedNodes(),
        equalTo(scaleSettings.getFixedScale().getTargetDedicatedNodes()));
    assertThat(
        result.fixedScale().targetLowPriorityNodes(),
        equalTo(scaleSettings.getFixedScale().getTargetLowPriorityNodes()));
  }

  @Test
  public void testAutoScaleSettings() {
    var autoScale =
        new ApiAzureBatchPoolAutoScaleSettings()
            .evaluationInterval(15) /*15 min default value*/
            .formula("formula");
    var scaleSettings = new ApiAzureBatchPoolScaleSettings().autoScale(autoScale);
    var result = MapperUtils.BatchPoolMapper.mapFrom(scaleSettings);

    assertNotNull(result);
    assertThat(
        result.autoScale().evaluationInterval().toMinutes(),
        equalTo(scaleSettings.getAutoScale().getEvaluationInterval().longValue()));
    assertThat(result.autoScale().formula(), equalTo(scaleSettings.getAutoScale().getFormula()));
  }

  @Test
  public void testNullableNetworkConfiguration() {
    assertNull(MapperUtils.BatchPoolMapper.mapFrom((ApiAzureBatchPoolNetworkConfiguration) null));
  }

  @Test
  public void testNetworkConfiguration() {
    var networkConfiguration = getApiAzureBatchPoolNetworkConfiguration();

    var result = MapperUtils.BatchPoolMapper.mapFrom(networkConfiguration);

    assertNotNull(result);
    assertThat(result.subnetId(), equalTo(networkConfiguration.getSubnetId()));
    assertThat(
        result.dynamicVNetAssignmentScope().toString(),
        equalTo(networkConfiguration.getDynamicVNetAssignmentScope().toString()));
    var endpointConfigurationResult = result.endpointConfiguration();
    assertNotNull(endpointConfigurationResult);
    assertThat(
        endpointConfigurationResult.inboundNatPools().size(),
        equalTo(networkConfiguration.getEndpointConfiguration().getInboundNatPools().size()));
    var natPoolResult = endpointConfigurationResult.inboundNatPools().get(0);
    assertThat(
        natPoolResult.name(),
        equalTo(
            networkConfiguration.getEndpointConfiguration().getInboundNatPools().get(0).getName()));
    assertThat(
        natPoolResult.protocol().toString(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getProtocol()
                .toString()));
    assertThat(
        natPoolResult.frontendPortRangeStart(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getFrontendPortRangeStart()));
    assertThat(
        natPoolResult.frontendPortRangeEnd(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getFrontendPortRangeEnd()));
    assertThat(
        natPoolResult.backendPort(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getBackendPort()));
    assertThat(
        natPoolResult.networkSecurityGroupRules().size(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getNetworkSecurityGroupRules()
                .size()));
    var securityRuleResult = natPoolResult.networkSecurityGroupRules().get(0);
    assertThat(
        securityRuleResult.access().toString(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getNetworkSecurityGroupRules()
                .get(0)
                .getAccess()
                .toString()));
    assertThat(
        securityRuleResult.sourcePortRanges().size(),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getNetworkSecurityGroupRules()
                .get(0)
                .getSourcePortRanges()
                .size()));
    assertThat(
        securityRuleResult.sourcePortRanges().get(0).contains("8081"),
        equalTo(
            networkConfiguration
                .getEndpointConfiguration()
                .getInboundNatPools()
                .get(0)
                .getNetworkSecurityGroupRules()
                .get(0)
                .getSourcePortRanges()
                .contains("8081")));
    var publicIpAddressConfigurationResult = result.publicIpAddressConfiguration();
    assertNotNull(publicIpAddressConfigurationResult);
    assertThat(
        publicIpAddressConfigurationResult.provision().toString(),
        equalTo(networkConfiguration.getPublicIpAddressConfiguration().getProvision().toString()));
    assertThat(
        publicIpAddressConfigurationResult.ipAddressIds().size(),
        equalTo(networkConfiguration.getPublicIpAddressConfiguration().getIpAddressIds().size()));
    assertThat(
        publicIpAddressConfigurationResult.ipAddressIds().contains("id1"),
        equalTo(
            networkConfiguration
                .getPublicIpAddressConfiguration()
                .getIpAddressIds()
                .contains("id1")));
  }

  @Test
  public void testNullableStartTask() {
    assertNull(MapperUtils.BatchPoolMapper.mapFrom((ApiAzureBatchPoolStartTask) null));
  }

  @Test
  public void testStartTask() {
    var startTask = createApiAzureBatchPoolStartTask();

    var startTaskResult = MapperUtils.BatchPoolMapper.mapFrom(startTask);

    assertNotNull(startTaskResult);
    assertThat(startTaskResult.commandLine(), equalTo(startTask.getCommandLine()));
    assertThat(startTaskResult.maxTaskRetryCount(), equalTo(startTask.getMaxTaskRetryCount()));
    assertThat(startTaskResult.waitForSuccess(), equalTo(startTask.isWaitForSuccess()));
    var userIdentityResult = startTaskResult.userIdentity();
    assertThat(userIdentityResult.username(), equalTo(startTask.getUserIdentity().getUserName()));
    var autoUserResult = userIdentityResult.autoUser();
    assertThat(
        autoUserResult.scope().toString(),
        equalTo(startTask.getUserIdentity().getAutoUser().getScope().toString()));
    assertThat(
        autoUserResult.elevationLevel().toString(),
        equalTo(startTask.getUserIdentity().getAutoUser().getElevationLevel().toString()));
    var resourceFilesResult = startTaskResult.resourceFiles();
    assertNotNull(resourceFilesResult);
    assertThat(resourceFilesResult.size(), equalTo(startTask.getResourceFiles().size()));
    assertEquals(1, resourceFilesResult.size());
    assertThat(
        resourceFilesResult.get(0).filePath(),
        equalTo(startTask.getResourceFiles().get(0).getFilePath()));
    assertThat(
        resourceFilesResult.get(0).fileMode(),
        equalTo(startTask.getResourceFiles().get(0).getFileMode()));
    assertThat(
        resourceFilesResult.get(0).autoStorageContainerName(),
        equalTo(startTask.getResourceFiles().get(0).getAutoStorageContainerName()));
    assertThat(
        resourceFilesResult.get(0).blobPrefix(),
        equalTo(startTask.getResourceFiles().get(0).getBlobPrefix()));
    assertThat(
        resourceFilesResult.get(0).httpUrl(),
        equalTo(startTask.getResourceFiles().get(0).getHttpUrl()));
    assertThat(
        resourceFilesResult.get(0).storageContainerUrl(),
        equalTo(startTask.getResourceFiles().get(0).getStorageContainerUrl()));
    assertThat(
        resourceFilesResult.get(0).identityReference().resourceId(),
        equalTo(startTask.getResourceFiles().get(0).getIdentityReference().getResourceId()));
    var environmentSettingsResult = startTaskResult.environmentSettings();
    assertNotNull(environmentSettingsResult);
    assertThat(
        environmentSettingsResult.size(), equalTo(startTask.getEnvironmentSettings().size()));
    assertEquals(1, environmentSettingsResult.size());
    assertThat(
        environmentSettingsResult.get(0).name(),
        equalTo(startTask.getEnvironmentSettings().get(0).getName()));
    assertThat(
        environmentSettingsResult.get(0).value(),
        equalTo(startTask.getEnvironmentSettings().get(0).getValue()));
    var containerSettingsResult = startTaskResult.containerSettings();
    assertNotNull(containerSettingsResult);
    assertThat(
        containerSettingsResult.containerRunOptions(),
        equalTo(startTask.getContainerSettings().getContainerRunOptions()));
    assertThat(
        containerSettingsResult.imageName(),
        equalTo(startTask.getContainerSettings().getImageName()));
    assertThat(
        containerSettingsResult.workingDirectory().toString(),
        equalTo(startTask.getContainerSettings().getWorkingDirectory().toString()));
    var containerRegistryResult = containerSettingsResult.registry();
    assertNotNull(containerRegistryResult);
    assertThat(
        containerRegistryResult.username(),
        equalTo(startTask.getContainerSettings().getRegistry().getUserName()));
    assertThat(
        containerRegistryResult.password(),
        equalTo(startTask.getContainerSettings().getRegistry().getPassword()));
    assertThat(
        containerRegistryResult.registryServer(),
        equalTo(startTask.getContainerSettings().getRegistry().getRegistryServer()));
    assertThat(
        containerRegistryResult.identityReference().resourceId(),
        equalTo(
            startTask.getContainerSettings().getRegistry().getIdentityReference().getResourceId()));
  }

  @Test
  public void testNullableListOfApplicationPackageReferences() {
    assertNull(MapperUtils.BatchPoolMapper.mapListOfApplicationPackageReferences(null));
  }

  @Test
  public void testEmptyListOfApplicationPackageReferences() {
    assertNull(
        MapperUtils.BatchPoolMapper.mapListOfApplicationPackageReferences(new ArrayList<>()));
  }

  @Test
  public void testListOfApplicationPackageReferences() {
    var listOfPackageReferences = List.of(createApiAzureBatchPoolApplicationPackageReference());

    var result =
        MapperUtils.BatchPoolMapper.mapListOfApplicationPackageReferences(listOfPackageReferences);

    assertNotNull(result);
    assertThat(result.size(), equalTo(listOfPackageReferences.size()));
    assertEquals(1, result.size());
    var packageReference = result.get(0);
    assertThat(packageReference.id(), equalTo(listOfPackageReferences.get(0).getId()));
    assertThat(packageReference.version(), equalTo(listOfPackageReferences.get(0).getVersion()));
  }

  @Test
  public void testNullableListOfMetadataItems() {
    assertNull(MapperUtils.BatchPoolMapper.mapListOfMetadataItems(null));
  }

  @Test
  public void testEmptyListOfMetadataItems() {
    assertNull(MapperUtils.BatchPoolMapper.mapListOfMetadataItems(new ArrayList<>()));
  }

  @Test
  public void testListOfMetadataItems() {
    var metadataItemList =
        List.of(
            new ApiAzureBatchPoolMetadataItem().name("name1").value("value1"),
            new ApiAzureBatchPoolMetadataItem().name("name2").value("value2"));

    var result = MapperUtils.BatchPoolMapper.mapListOfMetadataItems(metadataItemList);
    assertNotNull(result);
    assertEquals(metadataItemList.size(), result.size());
    var item1 = result.stream().filter(m -> m.name().equals("name1")).findFirst();
    assertTrue(item1.isPresent());
    assertEquals("value1", item1.get().value());
    var item2 = result.stream().filter(m -> m.name().equals("name2")).findFirst();
    assertTrue(item2.isPresent());
    assertEquals("value2", item2.get().value());
  }

  private static ApiAzureBatchPoolApplicationPackageReference
      createApiAzureBatchPoolApplicationPackageReference() {
    return new ApiAzureBatchPoolApplicationPackageReference()
        .id("packageId")
        .version("packageVersion");
  }

  private static ApiNetworkSecurityGroupRule getApiNetworkSecurityGroupRule() {
    return new ApiNetworkSecurityGroupRule()
        .priority(100)
        .access(ApiNetworkSecurityGroupRuleAccess.ALLOW)
        .sourceAddressPrefix("prefix")
        .sourcePortRanges(List.of("8081"));
  }

  private static ApiInboundNatPool getApiInboundNatPool() {
    return new ApiInboundNatPool()
        .name("natName")
        .protocol(ApiInboundEndpointProtocol.TCP)
        .backendPort(8080)
        .frontendPortRangeStart(8081)
        .frontendPortRangeEnd(8083)
        .networkSecurityGroupRules(List.of(getApiNetworkSecurityGroupRule()));
  }

  private static ApiPoolEndpointConfiguration getApiPoolEndpointConfiguration() {
    return new ApiPoolEndpointConfiguration().inboundNatPools(List.of(getApiInboundNatPool()));
  }

  private static ApiPublicIpAddressConfiguration getApiPublicIpAddressConfiguration() {
    return new ApiPublicIpAddressConfiguration()
        .provision(ApiIpAddressProvisioningType.BATCHMANAGED)
        .ipAddressIds(List.of("id1"));
  }

  private static ApiAzureBatchPoolNetworkConfiguration getApiAzureBatchPoolNetworkConfiguration() {
    return new ApiAzureBatchPoolNetworkConfiguration()
        .subnetId("subnetId")
        .dynamicVNetAssignmentScope(ApiDynamicVNetAssignmentScope.JOB)
        .endpointConfiguration(getApiPoolEndpointConfiguration())
        .publicIpAddressConfiguration(getApiPublicIpAddressConfiguration());
  }

  private static ApiAzureBatchPoolStartTask createApiAzureBatchPoolStartTask() {
    return new ApiAzureBatchPoolStartTask()
        .commandLine("/bin/bash")
        .resourceFiles(List.of(createApiAzureBatchPoolResourceFile()))
        .environmentSettings(List.of(createApiAzureBatchPoolEnvironmentSetting()))
        .userIdentity(createApiAzureBatchPoolUserIdentity())
        .maxTaskRetryCount(3)
        .waitForSuccess(Boolean.TRUE)
        .containerSettings(createApiAzureBatchPoolTaskContainerSettings());
  }

  private static ApiAzureBatchPoolTaskContainerSettings
      createApiAzureBatchPoolTaskContainerSettings() {
    return new ApiAzureBatchPoolTaskContainerSettings()
        .containerRunOptions("runOptions")
        .imageName("imageName")
        .registry(createApiAzureBatchPoolContainerRegistry())
        .workingDirectory(ApiAzureBatchPoolContainerWorkingDirectory.CONTAINERIMAGEDEFAULT);
  }

  private static ApiAzureBatchPoolContainerRegistry createApiAzureBatchPoolContainerRegistry() {
    return new ApiAzureBatchPoolContainerRegistry()
        .userName("userName")
        .password("password")
        .registryServer("registryService")
        .identityReference(createApiAzureBatchPoolComputeNodeIdentityReference());
  }

  private static ApiAzureBatchPoolResourceFile createApiAzureBatchPoolResourceFile() {
    return new ApiAzureBatchPoolResourceFile()
        .filePath("filePath")
        .fileMode("fileMode")
        .autoStorageContainerName("containerName")
        .blobPrefix("blobPrefix")
        .httpUrl("httpUrl")
        .storageContainerUrl("containerUrl")
        .identityReference(createApiAzureBatchPoolComputeNodeIdentityReference());
  }

  private static ApiAzureBatchPoolComputeNodeIdentityReference
      createApiAzureBatchPoolComputeNodeIdentityReference() {
    return new ApiAzureBatchPoolComputeNodeIdentityReference().resourceId("resourceId");
  }

  private static ApiAzureBatchPoolEnvironmentSetting createApiAzureBatchPoolEnvironmentSetting() {
    return new ApiAzureBatchPoolEnvironmentSetting().name("envName").value("envValue");
  }

  private static ApiAzureBatchPoolAutoUserSpecification
      createApiAzureBatchPoolAutoUserSpecification() {
    return new ApiAzureBatchPoolAutoUserSpecification()
        .scope(ApiAzureBatchPoolAutoUserScope.POOL)
        .elevationLevel(ApiAzureBatchPoolElevationLevel.ADMIN);
  }

  private static ApiAzureBatchPoolUserIdentity createApiAzureBatchPoolUserIdentity() {
    return new ApiAzureBatchPoolUserIdentity()
        .userName("identityUserName")
        .autoUser(createApiAzureBatchPoolAutoUserSpecification());
  }
}
