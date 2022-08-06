package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.service.landingzone.azure.AzureLandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.exception.AzureLandingZoneDeleteNotImplemented;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZone;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZoneDefinition;
import bio.terra.landingzone.service.landingzone.azure.model.AzureLandingZoneRequest;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.ControllerBase;
import bio.terra.workspace.common.utils.AzureVmUtils;
import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.controller.LandingZonesApi;
import bio.terra.workspace.generated.model.ApiAzureContext;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedAzureLandingZoneResult;
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
  private final AzureLandingZoneService azureLandingZoneService;
  private final FeatureConfiguration features;
  private final AzureLandingZoneManagerProvider azureLandingZoneManagerProvider;

  @Autowired
  public AzureLandingZoneApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      AzureLandingZoneService azureLandingZoneService,
      FeatureConfiguration features,
      AzureLandingZoneManagerProvider azureLandingZoneManagerProvider) {
    super(authenticatedUserRequestFactory, request, samService);
    this.azureLandingZoneService = azureLandingZoneService;
    this.features = features;
    this.azureLandingZoneManagerProvider = azureLandingZoneManagerProvider;
  }

  @Override
  public ResponseEntity<ApiCreatedAzureLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    features.isAzureEnabled();
    // AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info(
        "Requesting new Azure landing zone definition={}, version={}",
        body.getDefinition(),
        body.getVersion());

    ApiAzureContext azureContext =
        Optional.ofNullable(body.getAzureContext())
            .orElseThrow(
                () ->
                    new CloudContextRequiredException(
                        "AzureContext is required when creating an Azure landing zone"));

    AzureLandingZoneRequest azureLandingZoneDefinition =
        AzureLandingZoneRequest.builder()
            .definition(body.getDefinition())
            .version(body.getVersion())
            .parameters(AzureVmUtils.landingZoneFrom(body.getParameters()))
            .build();

    ControllerValidationUtils.validateAzureLandingZone(azureLandingZoneDefinition);

    AzureLandingZone azureLandingZone =
        azureLandingZoneService.createLandingZone(
            azureLandingZoneDefinition,
            azureLandingZoneManagerProvider.createLandingZoneManager(azureContext),
            azureContext.getResourceGroupId());

    ApiCreatedAzureLandingZoneResult result =
        new ApiCreatedAzureLandingZoneResult()
            .id(azureLandingZone.getId())
            .resources(
                azureLandingZone.getDeployedResources().stream()
                    .map(
                        dr ->
                            new ApiAzureLandingZoneDeployedResource()
                                .resourceId(dr.getResourceId())
                                .resourceType(dr.getResourceType())
                                .region(dr.getRegion()))
                    .toList());
    return new ResponseEntity<>(result, HttpStatus.CREATED);
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
