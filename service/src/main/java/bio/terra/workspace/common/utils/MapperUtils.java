package bio.terra.workspace.common.utils;

import bio.terra.landingzone.job.model.ErrorReport;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.lz.futureservice.model.AzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolApplicationPackageReference;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolAutoScaleSettings;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolCloudServiceConfiguration;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolContainerRegistry;
import bio.terra.workspace.generated.model.ApiAzureBatchPoolDeploymentConfiguration;
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
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiInboundNatPool;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiNetworkSecurityGroupRule;
import bio.terra.workspace.generated.model.ApiPoolEndpointConfiguration;
import bio.terra.workspace.generated.model.ApiPublicIpAddressConfiguration;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import com.azure.resourcemanager.batch.models.ApplicationPackageReference;
import com.azure.resourcemanager.batch.models.AutoScaleSettings;
import com.azure.resourcemanager.batch.models.AutoUserScope;
import com.azure.resourcemanager.batch.models.AutoUserSpecification;
import com.azure.resourcemanager.batch.models.CloudServiceConfiguration;
import com.azure.resourcemanager.batch.models.ComputeNodeDeallocationOption;
import com.azure.resourcemanager.batch.models.ComputeNodeIdentityReference;
import com.azure.resourcemanager.batch.models.ContainerRegistry;
import com.azure.resourcemanager.batch.models.ContainerWorkingDirectory;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.DynamicVNetAssignmentScope;
import com.azure.resourcemanager.batch.models.ElevationLevel;
import com.azure.resourcemanager.batch.models.EnvironmentSetting;
import com.azure.resourcemanager.batch.models.FixedScaleSettings;
import com.azure.resourcemanager.batch.models.ImageReference;
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
import com.azure.resourcemanager.batch.models.VirtualMachineConfiguration;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapperUtils {

  public static class LandingZoneMapper {
    private LandingZoneMapper() {}

    public static List<AzureLandingZoneParameter> apiClientLandingZoneParametersFrom(
        List<ApiAzureLandingZoneParameter> parametersList) {
      return nullSafeListToStream(parametersList)
          .map(
              param -> {
                var adapted = new AzureLandingZoneParameter();
                adapted.setKey(param.getKey());
                adapted.setValue(param.getValue());
                return adapted;
              })
          .toList();
    }

    public static HashMap<String, String> landingZoneParametersFrom(
        List<ApiAzureLandingZoneParameter> parametersList) {
      return nullSafeListToStream(parametersList)
          .flatMap(Stream::ofNullable)
          .collect(
              Collectors.toMap(
                  ApiAzureLandingZoneParameter::getKey,
                  ApiAzureLandingZoneParameter::getValue,
                  (prev, next) -> prev,
                  HashMap::new));
    }

    private static <T> Stream<T> nullSafeListToStream(Collection<T> collection) {
      return Optional.ofNullable(collection).stream().flatMap(Collection::stream);
    }
  }

  public static class JobReportMapper {
    private JobReportMapper() {}

    public static ApiJobReport from(JobReport jobReport) {
      return new ApiJobReport()
          .id(jobReport.getId())
          .description(jobReport.getDescription())
          .status(ApiJobReport.StatusEnum.valueOf(jobReport.getStatus().toString()))
          .statusCode(jobReport.getStatusCode())
          .submitted(jobReport.getSubmitted())
          .completed(jobReport.getCompleted())
          .resultURL(jobReport.getResultURL());
    }

    public static ApiJobReport fromLandingZoneApi(
        bio.terra.lz.futureservice.model.JobReport jobReport) {
      return new ApiJobReport()
          .id(jobReport.getId())
          .description(jobReport.getDescription())
          .status(ApiJobReport.StatusEnum.valueOf(jobReport.getStatus().toString()))
          .statusCode(jobReport.getStatusCode())
          .submitted(jobReport.getSubmitted())
          .completed(jobReport.getCompleted())
          .resultURL(jobReport.getResultURL());
    }
  }

  public static class ErrorReportMapper {
    private ErrorReportMapper() {}

    public static ApiErrorReport from(ErrorReport errorReport) {
      if (errorReport == null) {
        return null;
      }
      return new ApiErrorReport()
          .message(errorReport.getMessage())
          .statusCode(errorReport.getStatusCode())
          .causes(errorReport.getCauses());
    }

    public static ApiErrorReport fromLandingZoneApi(
        bio.terra.lz.futureservice.model.ErrorReport errorReport) {
      if (errorReport == null) {
        return null;
      }
      return new ApiErrorReport()
          .message(errorReport.getMessage())
          .statusCode(errorReport.getStatusCode())
          .causes(errorReport.getCauses());
    }
  }

  public static class BatchPoolMapper {
    public static List<BatchPoolUserAssignedManagedIdentity> mapListOfUserAssignedIdentities(
        List<ApiAzureBatchPoolUserAssignedIdentity> userAssignedManagedIdentities) {
      if (userAssignedManagedIdentities == null || userAssignedManagedIdentities.isEmpty()) {
        return null;
      }

      return userAssignedManagedIdentities.stream()
          .map(
              i ->
                  new BatchPoolUserAssignedManagedIdentity(
                      i.getResourceGroupName(), i.getName(), i.getClientId()))
          .collect(Collectors.toList());
    }

    public static DeploymentConfiguration mapFrom(
        ApiAzureBatchPoolDeploymentConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  c.getVirtualMachineConfiguration() != null
                      ? new DeploymentConfiguration()
                          .withVirtualMachineConfiguration(
                              mapFrom(c.getVirtualMachineConfiguration()))
                      : new DeploymentConfiguration()
                          .withCloudServiceConfiguration(mapFrom(c.getCloudServiceConfiguration())))
          .orElse(null);
    }

    public static ScaleSettings mapFrom(ApiAzureBatchPoolScaleSettings scaleSettings) {
      return Optional.ofNullable(scaleSettings)
          .map(
              sc ->
                  sc.getAutoScale() != null
                      ? new ScaleSettings().withAutoScale(mapFrom(sc.getAutoScale()))
                      : new ScaleSettings().withFixedScale(mapFrom(sc.getFixedScale())))
          .orElse(null);
    }

    public static NetworkConfiguration mapFrom(
        ApiAzureBatchPoolNetworkConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  new NetworkConfiguration()
                      .withSubnetId(c.getSubnetId())
                      .withDynamicVNetAssignmentScope(
                          DynamicVNetAssignmentScope.fromString(
                              c.getDynamicVNetAssignmentScope().toString()))
                      .withEndpointConfiguration(mapFrom(c.getEndpointConfiguration()))
                      .withPublicIpAddressConfiguration(
                          mapFrom(c.getPublicIpAddressConfiguration())))
          .orElse(null);
    }

    public static List<MetadataItem> mapListOfMetadataItems(
        List<ApiAzureBatchPoolMetadataItem> metadata) {
      if (metadata == null || metadata.isEmpty()) {
        return null;
      }

      return metadata.stream()
          .map(
              apiMetadata ->
                  new MetadataItem()
                      .withName(apiMetadata.getName())
                      .withValue(apiMetadata.getValue()))
          .collect(Collectors.toList());
    }

    public static StartTask mapFrom(ApiAzureBatchPoolStartTask startTask) {
      return Optional.ofNullable(startTask)
          .map(
              st ->
                  new StartTask()
                      .withCommandLine(st.getCommandLine())
                      .withResourceFiles(
                          st.getResourceFiles().stream().map(BatchPoolMapper::mapFrom).toList())
                      .withEnvironmentSettings(
                          st.getEnvironmentSettings().stream()
                              .map(BatchPoolMapper::mapFrom)
                              .toList())
                      .withUserIdentity(BatchPoolMapper.mapFrom(st.getUserIdentity()))
                      .withMaxTaskRetryCount(st.getMaxTaskRetryCount())
                      .withWaitForSuccess(st.isWaitForSuccess())
                      .withContainerSettings(mapFrom(st.getContainerSettings())))
          .orElse(null);
    }

    private static ContainerRegistry mapFrom(ApiAzureBatchPoolContainerRegistry registry) {
      return Optional.ofNullable(registry)
          .map(
              r ->
                  new ContainerRegistry()
                      .withUsername(r.getUserName())
                      .withPassword(r.getPassword())
                      .withRegistryServer(r.getRegistryServer())
                      .withIdentityReference(
                          new ComputeNodeIdentityReference()
                              .withResourceId(r.getIdentityReference().getResourceId())))
          .orElse(null);
    }

    public static List<ApplicationPackageReference> mapListOfApplicationPackageReferences(
        List<ApiAzureBatchPoolApplicationPackageReference> applicationPackages) {
      if (applicationPackages == null || applicationPackages.isEmpty()) {
        return null;
      }
      return applicationPackages.stream()
          .map(
              ap ->
                  new ApplicationPackageReference().withId(ap.getId()).withVersion(ap.getVersion()))
          .toList();
    }

    private static EnvironmentSetting mapFrom(
        ApiAzureBatchPoolEnvironmentSetting environmentSetting) {
      return Optional.ofNullable(environmentSetting)
          .map(es -> new EnvironmentSetting().withName(es.getName()).withValue(es.getValue()))
          .orElse(null);
    }

    private static UserIdentity mapFrom(ApiAzureBatchPoolUserIdentity userIdentity) {
      return Optional.ofNullable(userIdentity)
          .map(
              ui ->
                  new UserIdentity()
                      .withUsername(ui.getUserName())
                      .withAutoUser(
                          new AutoUserSpecification()
                              .withScope(
                                  AutoUserScope.fromString(ui.getAutoUser().getScope().toString()))
                              .withElevationLevel(
                                  ElevationLevel.fromString(
                                      ui.getAutoUser().getElevationLevel().toString()))))
          .orElse(null);
    }

    private static ResourceFile mapFrom(ApiAzureBatchPoolResourceFile resourceFile) {
      return Optional.ofNullable(resourceFile)
          .map(
              rf ->
                  new ResourceFile()
                      .withAutoStorageContainerName(rf.getAutoStorageContainerName())
                      .withStorageContainerUrl(rf.getStorageContainerUrl())
                      .withHttpUrl(rf.getHttpUrl())
                      .withBlobPrefix(rf.getBlobPrefix())
                      .withFilePath(rf.getFilePath())
                      .withFileMode(rf.getFileMode())
                      .withIdentityReference(
                          new ComputeNodeIdentityReference()
                              .withResourceId(rf.getIdentityReference().getResourceId())))
          .orElse(null);
    }

    private static VirtualMachineConfiguration mapFrom(
        ApiAzureBatchPoolVirtualMachineConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  new VirtualMachineConfiguration()
                      .withNodeAgentSkuId(c.getNodeAgentSkuId())
                      .withImageReference(mapFrom(c.getImageReference())))
          .orElse(null);
    }

    private static CloudServiceConfiguration mapFrom(
        ApiAzureBatchPoolCloudServiceConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  new CloudServiceConfiguration()
                      .withOsFamily(c.getOsFamily())
                      .withOsVersion(c.getOsVersion()))
          .orElse(null);
    }

    private static ImageReference mapFrom(
        ApiAzureBatchPoolVirtualMachineImageReference imageReference) {
      return Optional.ofNullable(imageReference)
          .map(
              ir ->
                  ir.getId() != null
                      ? new ImageReference().withId(ir.getId())
                      : new ImageReference()
                          .withPublisher(ir.getPublisher())
                          .withOffer(ir.getOffer())
                          .withSku(ir.getSku())
                          .withVersion(ir.getVersion()))
          .orElse(null);
    }

    private static AutoScaleSettings mapFrom(ApiAzureBatchPoolAutoScaleSettings settings) {
      return Optional.ofNullable(settings)
          .map(
              s ->
                  new AutoScaleSettings()
                      .withFormula(s.getFormula())
                      .withEvaluationInterval(Duration.ofMinutes(s.getEvaluationInterval())))
          .orElse(null);
    }

    private static FixedScaleSettings mapFrom(ApiAzureBatchPoolFixedScaleSettings settings) {
      return Optional.ofNullable(settings)
          .map(
              s ->
                  new FixedScaleSettings()
                      .withResizeTimeout(Duration.ofMinutes(s.getResizeTimeout()))
                      .withTargetDedicatedNodes(s.getTargetDedicatedNodes())
                      .withTargetLowPriorityNodes(s.getTargetLowPriorityNodes())
                      .withNodeDeallocationOption(
                          ComputeNodeDeallocationOption.fromString(
                              s.getNodeDeallocationOption().toString())))
          .orElse(null);
    }

    private static TaskContainerSettings mapFrom(
        ApiAzureBatchPoolTaskContainerSettings containerSettings) {
      return Optional.ofNullable(containerSettings)
          .map(
              cs ->
                  new TaskContainerSettings()
                      .withContainerRunOptions(cs.getContainerRunOptions())
                      .withImageName(cs.getImageName())
                      .withRegistry(mapFrom(cs.getRegistry()))
                      .withWorkingDirectory(
                          ContainerWorkingDirectory.fromString(
                              cs.getWorkingDirectory().toString())))
          .orElse(null);
    }

    private static List<NetworkSecurityGroupRule> mapListOfNetworkSecurityGroupRules(
        List<ApiNetworkSecurityGroupRule> rules) {
      if (rules == null || rules.isEmpty()) {
        return null;
      }
      return rules.stream()
          .map(
              r ->
                  new NetworkSecurityGroupRule()
                      .withPriority(r.getPriority())
                      .withAccess(
                          NetworkSecurityGroupRuleAccess.fromString(r.getAccess().toString()))
                      .withSourceAddressPrefix(r.getSourceAddressPrefix())
                      .withSourcePortRanges(r.getSourcePortRanges()))
          .toList();
    }

    private static List<InboundNatPool> mapListOfInboundNatPools(
        List<ApiInboundNatPool> inboundNatPools) {
      if (inboundNatPools == null || inboundNatPools.isEmpty()) {
        return null;
      }
      return inboundNatPools.stream()
          .map(
              np ->
                  new InboundNatPool()
                      .withName(np.getName())
                      .withProtocol(InboundEndpointProtocol.fromString(np.getProtocol().toString()))
                      .withBackendPort(np.getBackendPort())
                      .withFrontendPortRangeStart(np.getFrontendPortRangeStart())
                      .withFrontendPortRangeEnd(np.getFrontendPortRangeEnd())
                      .withNetworkSecurityGroupRules(
                          mapListOfNetworkSecurityGroupRules(np.getNetworkSecurityGroupRules())))
          .toList();
    }

    private static PublicIpAddressConfiguration mapFrom(
        ApiPublicIpAddressConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  new PublicIpAddressConfiguration()
                      .withProvision(
                          IpAddressProvisioningType.fromString(c.getProvision().toString()))
                      .withIpAddressIds(c.getIpAddressIds()))
          .orElse(null);
    }

    private static PoolEndpointConfiguration mapFrom(ApiPoolEndpointConfiguration configuration) {
      return Optional.ofNullable(configuration)
          .map(
              c ->
                  new PoolEndpointConfiguration()
                      .withInboundNatPools(mapListOfInboundNatPools(c.getInboundNatPools())))
          .orElse(null);
    }
  }
}
