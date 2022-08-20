package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.job.AzureLandingZoneJobService;
import bio.terra.landingzone.job.model.JobReport;
import bio.terra.landingzone.resource.ExternalResourceService;
import bio.terra.landingzone.resource.landingzone.ExternalLandingZoneResource;
import bio.terra.landingzone.service.landingzone.azure.AzureLandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.AzureLandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZoneDefinition;
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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextRequiredException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private final AzureLandingZoneService azureLandingZoneService;
  private final AzureLandingZoneJobService azureLandingZoneJobService;
  private final ExternalResourceService externalResourceService;
  private final FeatureConfiguration features;
  private final CrlService crlService;
  private final AzureConfiguration azureConfiguration;

  @Autowired
  public AzureLandingZoneApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      AzureLandingZoneService azureLandingZoneService,
      AzureLandingZoneJobService azureLandingZoneJobService,
      ExternalResourceService externalResourceService,
      CrlService crlService,
      AzureConfiguration azureConfiguration,
      FeatureConfiguration features) {
    super(authenticatedUserRequestFactory, request, samService);
    this.azureLandingZoneService = azureLandingZoneService;
    this.azureLandingZoneJobService = azureLandingZoneJobService;
    this.externalResourceService = externalResourceService;
    this.crlService = crlService;
    this.azureConfiguration = azureConfiguration;
    this.features = features;
  }

  @Override
  public ResponseEntity<ApiCreatedAzureLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    features.isAzureEnabled();
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    String landingZoneDetails = "definition='%s', version='%s', name='%s', description='%s'";
    logger.info(
        "Requesting new Azure landing zone with the following parameters: {}",
        String.format(
            landingZoneDetails,
            body.getDefinition(),
            body.getVersion(),
            body.getName(),
            Optional.ofNullable(body.getDefinition()).orElse("Not defined")));

    ApiAzureContext azureContext =
        Optional.ofNullable(body.getAzureContext())
            .orElseThrow(
                () ->
                    new CloudContextRequiredException(
                        "AzureContext is required when creating an Azure landing zone"));

    ExternalLandingZoneResource resource =
        ExternalLandingZoneResource.builder()
            .resourceId(UUID.randomUUID())
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(
                MapperUtils.LandingZoneMapper.landingZoneParametersFrom(body.getParameters()))
            .name(body.getName())
            .description(body.getDescription())
            .azureCloudContext(MapperUtils.AzureCloudContextMapper.from(azureContext))
            .build();
    String jobId =
        externalResourceService.createAzureLandingZone(
            body.getJobControl().getId(),
            resource,
            MapperUtils.AuthenticatedUserRequestMapper.from(userRequest),
            MapperUtils.AzureConfigurationMapper.from(azureConfiguration),
            getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));

    ApiCreatedAzureLandingZoneResult result = fetchCreateAzureLandingZoneResult(jobId);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiCreatedAzureLandingZoneResult> getCreateAzureLandingZoneResult(
      String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    // jobService.verifyUserAccess(jobId, userRequest, uuid);
    ApiCreatedAzureLandingZoneResult response = fetchCreateAzureLandingZoneResult(jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  private ApiCreatedAzureLandingZoneResult fetchCreateAzureLandingZoneResult(String jobId) {
    final AzureLandingZoneJobService.AsyncJobResult<AzureLandingZone> jobResult =
        azureLandingZoneJobService.retrieveAsyncJobResult(jobId, AzureLandingZone.class);

    ApiAzureLandingZone azureLandingZone = null;
    if (jobResult.getJobReport().getStatus().equals(JobReport.StatusEnum.SUCCEEDED)) {
      azureLandingZone =
          Optional.ofNullable(jobResult.getResult())
              .map(
                  lz ->
                      new ApiAzureLandingZone()
                          .id(lz.getId())
                          .resources(
                              lz.getDeployedResources().stream()
                                  .map(
                                      resource ->
                                          new ApiAzureLandingZoneDeployedResource()
                                              .region(resource.getRegion())
                                              .resourceType(resource.getResourceType())
                                              .resourceId(resource.getResourceId()))
                                  .collect(Collectors.toList())))
              .orElse(null);
    }

    return new ApiCreatedAzureLandingZoneResult()
        .jobReport(MapperUtils.JobReportMapper.from(jobResult.getJobReport()))
        .errorReport(MapperUtils.ErrorReportMapper.from(jobResult.getApiErrorReport()))
        .landingZone(azureLandingZone);
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneDefinitionList> listAzureLandingZonesDefinitions() {
    features.isAzureEnabled();
    // AuthenticatedUserRequest userRequest = getAuthenticatedInfo();

    List<AzureLandingZoneDefinition> templates =
        azureLandingZoneService.listLandingZoneDefinitions();

    ApiAzureLandingZoneDefinitionList result =
        new ApiAzureLandingZoneDefinitionList()
            .landingzones(
                templates.stream()
                    .map(
                        t ->
                            new ApiAzureLandingZoneDefinition()
                                .definition(t.getDefinition())
                                .name(t.getName())
                                .description(t.getDescription())
                                .version(t.getVersion()))
                    .collect(Collectors.toList()));
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteAzureLandingZone(
      @PathVariable("landingZoneId") String landingZoneId) {
    features.isAzureEnabled();
    // AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    try {
      azureLandingZoneService.deleteLandingZone(landingZoneId);
    } catch (AzureLandingZoneDeleteNotImplemented ex) {
      logger.info("Request to delete landing zone. Operation is not supported.");
      return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
