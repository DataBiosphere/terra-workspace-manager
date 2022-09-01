package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.workspace.amalgam.landingzone.azure.utils.MapperUtils;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiLandingZoneTarget;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

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
      @RequestBody ApiCreateAzureLandingZoneRequestBody body, String asyncResultEndpoint) {
    features.azureEnabledCheck();
    String landingZoneDetails = "definition='%s', version='%s'";
    logger.info(
        "Requesting new Azure landing zone with the following parameters: {}",
        String.format(landingZoneDetails, body.getDefinition(), body.getVersion()));

    ApiLandingZoneTarget apiLandingZoneTarget =
        Optional.ofNullable(body.getLandingZoneTarget())
            .orElseThrow(
                () ->
                    new LandingZoneInvalidInputException(
                        "LandingZoneTarget is required when creating an Azure landing zone"));

    LandingZoneRequest landingZoneRequest =
        LandingZoneRequest.builder()
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(
                MapperUtils.LandingZoneMapper.landingZoneParametersFrom(body.getParameters()))
            .azureCloudContext(MapperUtils.AzureCloudContextMapper.from(apiLandingZoneTarget))
            .build();
    String jobId =
        landingZoneService.startLandingZoneCreationJob(
            body.getJobControl().getId(), landingZoneRequest, asyncResultEndpoint);

    return fetchCreateAzureLandingZoneResult(jobId);
  }

  public ApiAzureLandingZoneResult getCreateAzureLandingZoneResult(String jobId) {
    features.azureEnabledCheck();
    return fetchCreateAzureLandingZoneResult(jobId);
  }

  public ApiAzureLandingZoneDefinitionList listAzureLandingZonesDefinitions() {
    features.azureEnabledCheck();
    List<LandingZoneDefinition> templates = landingZoneService.listLandingZoneDefinitions();

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

  public void deleteLandingZone(String landingZoneId) {
    features.azureEnabledCheck();
    landingZoneService.deleteLandingZone(landingZoneId);
  }

  private ApiAzureLandingZoneResult fetchCreateAzureLandingZoneResult(String jobId) {
    final LandingZoneJobService.AsyncJobResult<DeployedLandingZone> jobResult =
        landingZoneService.getAsyncJobResult(jobId);

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
}
