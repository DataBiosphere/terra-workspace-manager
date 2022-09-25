package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.service.landingzone.azure.exception.LandingZoneDeleteNotImplemented;
import bio.terra.workspace.app.controller.ControllerBase;
import bio.terra.workspace.generated.controller.LandingZonesApi;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@Controller
public class LandingZoneApiController extends ControllerBase implements LandingZonesApi {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneApiController.class);
  private final LandingZoneApiDispatch landingZoneApiDispatch;

  @Autowired
  public LandingZoneApiController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      SamService samService,
      LandingZoneApiDispatch landingZoneApiDispatch) {
    super(authenticatedUserRequestFactory, request, samService);
    this.landingZoneApiDispatch = landingZoneApiDispatch;
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiAzureLandingZoneResult result =
        landingZoneApiDispatch.createAzureLandingZone(
                userRequest, body, getAsyncResultEndpoint(body.getJobControl().getId(), "create-result"));
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResult> getCreateAzureLandingZoneResult(String jobId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiAzureLandingZoneResult response =
        landingZoneApiDispatch.getCreateAzureLandingZoneResult(userRequest, jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneDefinitionList> listAzureLandingZonesDefinitions() {
    ApiAzureLandingZoneDefinitionList result =
        landingZoneApiDispatch.listAzureLandingZonesDefinitions();
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResourcesList> listAzureLandingZoneResources(
      @PathVariable("landingZoneId") UUID landingZoneId) {
    AuthenticatedUserRequest userRequest = getAuthenticatedInfo();
    ApiAzureLandingZoneResourcesList result =
        landingZoneApiDispatch.listAzureLandingZoneResources(userRequest, landingZoneId);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Void> deleteAzureLandingZone(
      @PathVariable("landingZoneId") UUID landingZoneId) {
    try {
      landingZoneApiDispatch.deleteLandingZone(landingZoneId);
    } catch (LandingZoneDeleteNotImplemented ex) {
      logger.info("Request to delete landing zone. Operation is not supported.");
      return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }
}
