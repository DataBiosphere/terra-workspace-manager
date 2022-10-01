package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.landingzone.library.landingzones.deployment.SubnetResourcePurpose;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneResourcesByPurpose;
import bio.terra.workspace.amalgam.landingzone.azure.utils.MapperUtils;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneApiDispatch {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneApiDispatch.class);

  private final LandingZoneService landingZoneService;
  private final FeatureConfiguration features;

  public LandingZoneApiDispatch(
      LandingZoneService landingZoneService, FeatureConfiguration features) {
    this.landingZoneService = landingZoneService;
    this.features = features;
  }

  public ApiAzureLandingZoneResult createAzureLandingZone(
      BearerToken bearerToken,
      ApiCreateAzureLandingZoneRequestBody body,
      String asyncResultEndpoint) {
    features.azureEnabledCheck();
    logger.info(
        "Requesting new Azure landing zone with definition='{}', version='{}'",
        body.getDefinition(),
        body.getVersion());

    // Prevent deploying more than 1 landing zone per billing profile
    landingZoneService.listLandingZoneIds(bearerToken, body.getBillingProfileId()).stream()
        .findFirst()
        .ifPresent(
            t -> {
              throw new LandingZoneInvalidInputException(
                  "A Landing Zone already exists in the requested billing profile");
            });

    LandingZoneRequest landingZoneRequest =
        LandingZoneRequest.builder()
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(
                MapperUtils.LandingZoneMapper.landingZoneParametersFrom(body.getParameters()))
            .billingProfileId(body.getBillingProfileId())
            .build();
    return toApiAzureLandingZoneResult(
        landingZoneService.startLandingZoneCreationJob(
            bearerToken, body.getJobControl().getId(), landingZoneRequest, asyncResultEndpoint));
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

  public void deleteLandingZone(BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    landingZoneService.deleteLandingZone(bearerToken, landingZoneId);
  }

  public ApiAzureLandingZoneResourcesList listAzureLandingZoneResources(
      BearerToken bearerToken, UUID landingZoneId) {
    features.azureEnabledCheck();
    LandingZoneResourcesByPurpose groupedResources =
        landingZoneService.listResourcesWithPurposes(bearerToken, landingZoneId);

    var result = new ApiAzureLandingZoneResourcesList().id(landingZoneId);

    groupedResources
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
      String landingZoneId) {
    features.azureEnabledCheck();
    String azureStorageAccountResourceType = "Microsoft.Storage/storageAccounts";
    return listAzureLandingZoneResources(landingZoneId).getResources().stream()
        .filter(rpg -> rpg.getPurpose().equals(ResourcePurpose.SHARED_RESOURCE.toString()))
        .flatMap(r -> r.getDeployedResources().stream())
        .filter(
            r -> StringUtils.equalsIgnoreCase(r.getResourceType(), azureStorageAccountResourceType))
        .findFirst();
  }

  public List<ApiAzureLandingZoneDeployedResource> listSubnetsWithParentVNetByPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose purpose) {

    return listAzureLandingZoneResources(bearerToken, landingZoneId).getResources().stream()
        .filter(r -> r.getPurpose().equals(purpose.toString()))
        .flatMap(r -> r.getDeployedResources().stream())
        .collect(Collectors.toList());
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
    ApiAzureLandingZone azureLandingZone = null;
    if (jobResult.getJobReport().getStatus().equals(JobReport.StatusEnum.SUCCEEDED)) {
      azureLandingZone =
          Optional.ofNullable(jobResult.getResult())
              .map(
                  lz ->
                      new ApiAzureLandingZone()
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

  /**
   * TODO (https://broadworkbench.atlassian.net/browse/TOAZ-221) This method is an initial
   * implementation that must be revised, and likely be refactored once WSM stores the LZ id in the
   * azure context. The initial assumption is that the cardinality of 1:1 between the cloud context
   * and the LZ is enforced in the create operation, therefore more than one LZ per azure context is
   * not allowed.
   */
  public UUID getLandingZoneId(AzureCloudContext azureCloudContext) {
    var landingZoneTarget = MapperUtils.LandingZoneTargetMapper.from(azureCloudContext);
    return landingZoneService.listLandingZoneIdsByTarget(landingZoneTarget).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not find a landing zone id for the given Azure context. Please check that the landing zone deployment is complete."));
  }
}
