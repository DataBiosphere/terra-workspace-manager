package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.LandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.DeployedLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZoneRequest;
import bio.terra.workspace.amalgam.landingzone.azure.utils.MapperUtils;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.ControllerBase;
import bio.terra.workspace.generated.controller.LandingZonesApi;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedAzureLandingZoneResult;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class AzureLandingZoneApiController extends ControllerBase implements LandingZonesApi {
  private static final Logger logger = LoggerFactory.getLogger(AzureLandingZoneApiController.class);
  private final LandingZoneService landingZoneService;
  private final FeatureConfiguration features;
  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;

  @Autowired
  public AzureLandingZoneApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      LandingZoneService landingZoneService,
      CrlService crlService,
      AzureConfiguration azureConfiguration,
      FeatureConfiguration features) {
    super(authenticatedUserRequestFactory, request, samService);
    this.landingZoneService = landingZoneService;
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
    this.features = features;
  }

  @Override
  public ResponseEntity<ApiCreatedAzureLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    features.isAzureEnabled();
    String landingZoneDetails = "definition='%s', version='%s'";
    logger.info(
        "Requesting new Azure landing zone with the following parameters: {}",
        String.format(landingZoneDetails, body.getDefinition(), body.getVersion()));

    ApiAzureContext azureContext =
        Optional.ofNullable(body.getAzureContext())
            .orElseThrow(
                () ->
                    new CloudContextRequiredException(
                        "AzureContext is required when creating an Azure landing zone"));

    LandingZoneRequest landingZoneRequest =
        LandingZoneRequest.builder()
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(
                MapperUtils.LandingZoneMapper.landingZoneParametersFrom(body.getParameters()))
            .azureCloudContext(MapperUtils.AzureCloudContextMapper.from(azureContext))
            .build();
    String jobId =
        landingZoneService.startLandingZoneCreationJob(
            body.getJobControl().getId(),
            landingZoneRequest,
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    ApiCreatedAzureLandingZoneResult result = fetchCreateAzureLandingZoneResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedAzureLandingZoneResult> getCreateAzureLandingZoneResult(
      String jobId) {
    ApiCreatedAzureLandingZoneResult response = fetchCreateAzureLandingZoneResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneDefinitionList> listAzureLandingZonesDefinitions() {
    features.isAzureEnabled();
    List<LandingZoneDefinition> templates = landingZoneService.listLandingZoneDefinitions();

    ApiAzureLandingZoneDefinitionList result =
        new ApiAzureLandingZoneDefinitionList()
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
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteAzureLandingZone(
      @PathVariable("landingZoneId") String landingZoneId) {
    features.isAzureEnabled();
    try {
      landingZoneService.deleteLandingZone(landingZoneId);
    } catch (LandingZoneDeleteNotImplemented ex) {
      logger.info("Request to delete landing zone. Operation is not supported.");
      return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  private ApiCreatedAzureLandingZoneResult fetchCreateAzureLandingZoneResult(String jobId) {
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

    return new ApiCreatedAzureLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZone(azureLandingZone);
  }
}
