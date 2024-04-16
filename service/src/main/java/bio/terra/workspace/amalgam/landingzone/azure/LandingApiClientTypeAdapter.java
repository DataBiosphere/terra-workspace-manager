package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.library.landingzones.management.quotas.ResourceQuota;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneDeletion;
import bio.terra.workspace.common.utils.MapperUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDetails;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Utilities for transforming from internal landing zone library types to externally facing API
 * types.
 */
public class LandingApiClientTypeAdapter {

  public ApiCreateLandingZoneResult toApiCreateLandingZoneResult(
      LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> jobResult) {

    return new ApiCreateLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZoneId(jobResult.getResult().landingZoneId())
        .definition(jobResult.getResult().definition())
        .version(jobResult.getResult().version());
  }

  public ApiAzureLandingZone toApiAzureLandingZone(LandingZone landingZone) {
    return new ApiAzureLandingZone()
        .billingProfileId(landingZone.billingProfileId())
        .landingZoneId(landingZone.landingZoneId())
        .definition(landingZone.definition())
        .version(landingZone.version())
        .region(landingZone.region())
        .createdDate(landingZone.createdDate());
  }

  public ApiDeleteAzureLandingZoneResult toApiDeleteAzureLandingZoneResult(
      LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion> jobResult) {
    return new ApiDeleteAzureLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZoneId(jobResult.getResult().landingZoneId());
  }

  public ApiAzureLandingZoneDeployedResource toApiAzureLandingZoneDeployedResource(
      LandingZoneResource resource, LandingZonePurpose purpose) {
    if (purpose.getClass().equals(ResourcePurpose.class)) {
      return new ApiAzureLandingZoneDeployedResource()
          .resourceId(resource.resourceId())
          .resourceType(resource.resourceType())
          .tags(resource.tags())
          .region(resource.region());
    }
    if (purpose.getClass().equals(SubnetResourcePurpose.class)) {
      return new ApiAzureLandingZoneDeployedResource()
          .resourceParentId(resource.resourceParentId().orElse(null)) // Only available for subnets
          .resourceName(resource.resourceName().orElse(null)) // Only available for subnets
          .resourceType(resource.resourceType())
          .resourceId(resource.resourceId())
          .tags(resource.tags())
          .region(resource.region());
    }
    throw new LandingZoneUnsupportedPurposeException(
        String.format(
            "Support for purpose type %s is not implemented.", purpose.getClass().getSimpleName()));
  }

  public ApiDeleteAzureLandingZoneJobResult toApiDeleteAzureLandingZoneJobResult(
      LandingZoneJobService.AsyncJobResult<DeletedLandingZone> jobResult) {
    var apiJobResult =
        new ApiDeleteAzureLandingZoneJobResult()
            .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
            .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()));

    if (jobResult.getJobReport().getStatus().equals(JobReport.StatusEnum.SUCCEEDED)) {
      apiJobResult.landingZoneId(jobResult.getResult().landingZoneId());
      apiJobResult.resources(jobResult.getResult().deleteResources());
    }
    return apiJobResult;
  }

  public ApiResourceQuota toApiResourceQuota(UUID landingZoneId, ResourceQuota resourceQuota) {
    return new ApiResourceQuota()
        .landingZoneId(landingZoneId)
        .azureResourceId(resourceQuota.resourceId())
        .resourceType(resourceQuota.resourceType())
        .quotaValues(resourceQuota.quota());
  }

  public ApiAzureLandingZoneResult toApiAzureLandingZoneResult(
      LandingZoneJobService.AsyncJobResult<DeployedLandingZone> jobResult) {
    ApiAzureLandingZoneDetails azureLandingZone = null;
    if (jobResult.getJobReport().getStatus().equals(JobReport.StatusEnum.SUCCEEDED)) {
      azureLandingZone =
          Optional.ofNullable(jobResult.getResult())
              .map(
                  lz ->
                      new ApiAzureLandingZoneDetails()
                          .id(lz.id())
                          .resources(
                              lz.deployedResources().stream()
                                  .map(
                                      resource ->
                                          new ApiAzureLandingZoneDeployedResource()
                                              .region(resource.region())
                                              .resourceType(resource.resourceType())
                                              .resourceId(resource.resourceId()))
                                  .collect(Collectors.toList())))
              .orElse(null);
    }

    return new ApiAzureLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZone(azureLandingZone);
  }
}
