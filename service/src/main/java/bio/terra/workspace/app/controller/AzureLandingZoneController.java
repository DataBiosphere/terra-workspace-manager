package bio.terra.workspace.app.controller;

import bio.terra.workspace.generated.controller.LandingZonesApi;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreatedAzureLandingZoneResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.lzm.AzureLandingZoneService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class AzureLandingZoneController extends ControllerBase implements LandingZonesApi {
  private static final Logger logger = LoggerFactory.getLogger(AzureLandingZoneController.class);
  private final AzureLandingZoneService azureLandingZoneService;

  @Autowired
  public AzureLandingZoneController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      AzureLandingZoneService azureLandingZoneService) {
    super(authenticatedUserRequestFactory, request, samService);
    this.azureLandingZoneService = azureLandingZoneService;
  }

  public ResponseEntity<ApiCreatedAzureLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    // AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    logger.info("Creating new Azure landing zone {} {}", body.getFactory(), body.getVersion());

    // TODO: validation
    // ControllerValidationUtils.validateRequestedAzureLandingZone(userFacingId);

    azureLandingZoneService.createLandingZone(body.getFactory(), body.getVersion());

    ApiCreatedAzureLandingZoneResult result =
        new ApiCreatedAzureLandingZoneResult().id("AZURE_LANDING_ZONE_ID");
    return new ResponseEntity<>(result, HttpStatus.OK);
  }
}
