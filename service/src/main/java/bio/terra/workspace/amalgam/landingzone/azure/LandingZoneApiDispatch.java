package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ConflictException;
import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.library.landingzones.management.quotas.ResourceQuota;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.DeletedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneCreation;
import bio.terra.landingzone.service.landingzone.azure.model.StartLandingZoneDeletion;
import bio.terra.workspace.amalgam.landingzone.azure.utils.MapperUtils;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDetails;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneApiDispatch {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneApiDispatch.class);

  private static final String AZURE_STORAGE_ACCOUNT_RESOURCE_TYPE =
      "Microsoft.Storage/storageAccounts";

  private final LandingZoneService landingZoneService;
  private final WorkspaceService workspaceService;
  private final FeatureConfiguration features;

  public LandingZoneApiDispatch(
      LandingZoneService landingZoneService,
      WorkspaceService workspaceService,
      FeatureConfiguration features) {
    this.landingZoneService = landingZoneService;
    this.workspaceService = workspaceService;
    this.features = features;
  }

  public ApiCreateLandingZoneResult createAzureLandingZone(
      BearerToken bearerToken,
      ApiCreateAzureLandingZoneRequestBody body,
      String asyncResultEndpoint) {
    features.azureEnabledCheck();
    logger.info(
        "Requesting new Azure landing zone with definition='{}', version='{}'",
        body.getDefinition(),
        body.getVersion());

    // Prevent deploying more than 1 landing zone per billing profile
    verifyLandingZoneDoesNotExistForBillingProfile(bearerToken, body);

    LandingZoneRequest landingZoneRequest =
        LandingZoneRequest.builder()
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(
                MapperUtils.LandingZoneMapper.landingZoneParametersFrom(body.getParameters()))
            .billingProfileId(body.getBillingProfileId())
            .build();
    return toApiCreateLandingZoneResult(
        landingZoneService.startLandingZoneCreationJob(
            bearerToken, body.getJobControl().getId(), landingZoneRequest, asyncResultEndpoint));
  }

  private void verifyLandingZoneDoesNotExistForBillingProfile(
      BearerToken bearerToken, ApiCreateAzureLandingZoneRequestBody body) {
    // TODO: Catching the exception is a temp solution.
    // A better approach would be to return an empty list instead of throwing an exception
    try {
      landingZoneService
          .getLandingZonesByBillingProfile(bearerToken, body.getBillingProfileId())
          .stream()
          .findFirst()
          .ifPresent(
              t -> {
                throw new LandingZoneInvalidInputException(
                    "A Landing Zone already exists in the requested billing profile");
              });
    } catch (bio.terra.landingzone.db.exception.LandingZoneNotFoundException ex) {
      logger.info("The billing profile does not have a landing zone. ", ex);
    }
  }

  private ApiCreateLandingZoneResult toApiCreateLandingZoneResult(
      LandingZoneJobService.AsyncJobResult<StartLandingZoneCreation> jobResult) {

    return new ApiCreateLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZoneId(jobResult.getResult().landingZoneId())
        .definition(jobResult.getResult().definition())
        .version(jobResult.getResult().version());
  }

  public ApiAzureLandingZoneResult getCreateAzureLandingZoneResult(
      BearerToken bearerToken, String jobId) {
    features.azureEnabledCheck();
    return toApiAzureLandingZoneResult(landingZoneService.getAsyncJobResult(bearerToken, jobId));
  }

  public ApiAzureLandingZoneDefinitionList listAzureLandingZonesDefinitions(
      BearerToken bearerToken) {
    features.azureEnabledCheck();
    List<LandingZoneDefinition> templates =
        landingZoneService.listLandingZoneDefinitions(bearerToken);

    return new ApiAzureLandingZoneDefinitionList()
        .landingzones(
            templates.stream()
                .map(
                    t ->
                        new ApiAzureLandingZoneDefinition()
                            .definition(t.definition())
                            .name(t.name())
                            .description(t.description())
                            .version(t.version()))
                .collect(Collectors.toList()));
  }

  public ApiDeleteAzureLandingZoneResult deleteLandingZone(
      BearerToken bearerToken,
      UUID landingZoneId,
      ApiDeleteAzureLandingZoneRequestBody body,
      String resultEndpoint) {
    features.azureEnabledCheck();
    return toApiDeleteAzureLandingZoneResult(
        landingZoneService.startLandingZoneDeletionJob(
            bearerToken, body.getJobControl().getId(), landingZoneId, resultEndpoint));
  }

  private ApiDeleteAzureLandingZoneResult toApiDeleteAzureLandingZoneResult(
      LandingZoneJobService.AsyncJobResult<StartLandingZoneDeletion> jobResult) {

    ApiDeleteAzureLandingZoneResult result =
        new ApiDeleteAzureLandingZoneResult()
            .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
            .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
            .landingZoneId(jobResult.getResult().landingZoneId());

    return result;
  }

  public ApiAzureLandingZoneResourcesList listAzureLandingZoneResources(
      BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    var result = new ApiAzureLandingZoneResourcesList().id(landingZoneId);
    landingZoneService
        .listResourcesWithPurposes(bearerToken, landingZoneId)
        .deployedResources()
        .forEach(
            (p, dp) ->
                result.addResourcesItem(
                    new ApiAzureLandingZoneResourcesPurposeGroup()
                        .purpose(p.toString())
                        .deployedResources(
                            dp.stream()
                                .map(r -> toApiAzureLandingZoneDeployedResource(r, p))
                                .toList())));
    return result;
  }

  public Optional<ApiAzureLandingZoneDeployedResource> getSharedStorageAccount(
      BearerToken bearerToken, UUID landingZoneId) {
    return listAzureLandingZoneResourcesByPurpose(
            bearerToken, landingZoneId, ResourcePurpose.SHARED_RESOURCE)
        .getResources()
        .stream()
        .flatMap(r -> r.getDeployedResources().stream())
        .filter(
            r ->
                StringUtils.equalsIgnoreCase(
                    r.getResourceType(), AZURE_STORAGE_ACCOUNT_RESOURCE_TYPE))
        .findFirst();
  }

  public ApiAzureLandingZoneResourcesList listAzureLandingZoneResourcesByPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose) {
    features.azureEnabledCheck();
    var result = new ApiAzureLandingZoneResourcesList().id(landingZoneId);
    var deployedResources =
        landingZoneService
            .listResourcesByPurpose(bearerToken, landingZoneId, resourcePurpose)
            .stream()
            .map(r -> toApiAzureLandingZoneDeployedResource(r, resourcePurpose))
            .toList();
    result.addResourcesItem(
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose(resourcePurpose.toString())
            .deployedResources(deployedResources));
    return result;
  }

  private ApiAzureLandingZoneDeployedResource toApiAzureLandingZoneDeployedResource(
      LandingZoneResource resource, LandingZonePurpose purpose) {
    if (purpose.getClass().equals(ResourcePurpose.class)) {
      return new ApiAzureLandingZoneDeployedResource()
          .resourceId(resource.resourceId())
          .resourceType(resource.resourceType())
          .region(resource.region());
    }
    if (purpose.getClass().equals(SubnetResourcePurpose.class)) {
      return new ApiAzureLandingZoneDeployedResource()
          .resourceParentId(resource.resourceParentId().get()) // Only available for subnets
          .resourceName(resource.resourceName().get()) // Only available for subnets
          .resourceType(resource.resourceType())
          .region(resource.region());
    }
    throw new LandingZoneUnsupportedPurposeException(
        String.format(
            "Support for purpose type %s is not implemented.", purpose.getClass().getSimpleName()));
  }

  private ApiAzureLandingZoneResult toApiAzureLandingZoneResult(
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

  public UUID getLandingZoneId(BearerToken token, UUID workspaceId) {
    Workspace workspace = workspaceService.getWorkspace(workspaceId);
    Optional<UUID> profileId = workspace.getSpendProfileId().map(sp -> UUID.fromString(sp.getId()));

    if (profileId.isEmpty()) {
      throw new LandingZoneNotFoundException(
          String.format(
              "Landing zone could not be found. Workspace Id=%s doesn't have billing profile.",
              workspace.getWorkspaceId()));
    }

    // getLandingZonesByBillingProfile returns a list. But it always contains only one item
    return landingZoneService.getLandingZonesByBillingProfile(token, profileId.get()).stream()
        .findFirst()
        .map(LandingZone::landingZoneId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Could not find a landing zone for the given billing profile: '%s'. Please"
                            + " check that the landing zone deployment is complete.",
                        profileId.get())));
  }

  public ApiDeleteAzureLandingZoneJobResult getDeleteAzureLandingZoneResult(
      BearerToken token, UUID landingZoneId, String jobId) {
    features.azureEnabledCheck();
    return toApiDeleteAzureLandingZoneJobResult(
        landingZoneService.getAsyncDeletionJobResult(token, landingZoneId, jobId));
  }

  private ApiDeleteAzureLandingZoneJobResult toApiDeleteAzureLandingZoneJobResult(
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

  public ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    LandingZone landingZoneRecord = landingZoneService.getLandingZone(bearerToken, landingZoneId);
    return toApiAzureLandingZone(landingZoneRecord);
  }

  public ApiAzureLandingZoneList listAzureLandingZones(
      BearerToken bearerToken, UUID billingProfileId) {
    features.azureEnabledCheck();
    if (billingProfileId != null) {
      return getAzureLandingZonesByBillingProfile(bearerToken, billingProfileId);
    }
    List<LandingZone> landingZones = landingZoneService.listLandingZones(bearerToken);
    return new ApiAzureLandingZoneList()
        .landingzones(
            landingZones.stream().map(this::toApiAzureLandingZone).collect(Collectors.toList()));
  }

  private ApiAzureLandingZoneList getAzureLandingZonesByBillingProfile(
      BearerToken bearerToken, UUID billingProfileId) {
    ApiAzureLandingZoneList result = new ApiAzureLandingZoneList();
    List<LandingZone> landingZones =
        landingZoneService.getLandingZonesByBillingProfile(bearerToken, billingProfileId);
    if (landingZones.size() > 0) {
      // The enforced logic is 1:1 relation between Billing Profile and a Landing Zone.
      // The landing zone service returns one record in the list if landing zone exists
      // for a given billing profile.
      if (landingZones.size() == 1) {
        result.addLandingzonesItem(toApiAzureLandingZone(landingZones.get(0)));
      } else {
        throw new ConflictException(
            String.format(
                "There are more than one landing zone found for the given billing profile: '%s'. Please"
                    + " check the landing zone deployment is correct.",
                billingProfileId));
      }
    }
    return result;
  }

  private ApiAzureLandingZone toApiAzureLandingZone(LandingZone landingZone) {
    return new ApiAzureLandingZone()
        .billingProfileId(landingZone.billingProfileId())
        .landingZoneId(landingZone.landingZoneId())
        .definition(landingZone.definition())
        .version(landingZone.version())
        .createdDate(landingZone.createdDate());
  }

  public ApiResourceQuota getResourceQuota(
      BearerToken bearerToken, UUID landingZoneId, String azureResourceId) {
    return toApiResourceQuota(landingZoneId, landingZoneService.getResourceQuota(bearerToken, landingZoneId, azureResourceId));
  }

  private ApiResourceQuota toApiResourceQuota(UUID landingZoneId, ResourceQuota resourceQuota) {
    return new ApiResourceQuota()
            .landingZoneId(landingZoneId)
            .azureResourceId(resourceQuota.resourceId())
            .resourceType(resourceQuota.resourceType())
            .quotaValues(resourceQuota.quota());
  }
}
