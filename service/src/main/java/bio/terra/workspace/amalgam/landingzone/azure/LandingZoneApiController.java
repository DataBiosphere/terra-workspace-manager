package bio.terra.workspace.amalgam.landingzone.azure;

import static bio.terra.workspace.app.controller.ControllerBase.getAsyncResponseCode;

import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.workspace.generated.controller.LandingZonesApi;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneRequestBody;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LandingZoneApiController implements LandingZonesApi {
  private static final Logger logger = LoggerFactory.getLogger(LandingZoneApiController.class);
  private final HttpServletRequest request;
  private final BearerTokenFactory bearerTokenFactory;
  private final LandingZoneApiDispatch landingZoneApiDispatch;

  @Autowired
  public LandingZoneApiController(
      HttpServletRequest request,
      BearerTokenFactory bearerTokenFactory,
      LandingZoneApiDispatch landingZoneApiDispatch) {
    this.request = request;
    this.bearerTokenFactory = bearerTokenFactory;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
  }

  @Override
  public ResponseEntity<ApiCreateLandingZoneResult> createAzureLandingZone(
      @RequestBody ApiCreateAzureLandingZoneRequestBody body) {
    String resultEndpoint =
        String.format(
            "%s/%s/%s", request.getServletPath(), "create-result", body.getJobControl().getId());
    ApiCreateLandingZoneResult result =
        landingZoneApiDispatch.createAzureLandingZone(
            bearerTokenFactory.from(request), body, resultEndpoint);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResult> getCreateAzureLandingZoneResult(String jobId) {
    ApiAzureLandingZoneResult response =
        landingZoneApiDispatch.getCreateAzureLandingZoneResult(
            bearerTokenFactory.from(request), jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneDefinitionList> listAzureLandingZonesDefinitions() {
    ApiAzureLandingZoneDefinitionList result =
        landingZoneApiDispatch.listAzureLandingZonesDefinitions(bearerTokenFactory.from(request));
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneResourcesList> listAzureLandingZoneResources(
      @PathVariable("landingZoneId") UUID landingZoneId) {
    ApiAzureLandingZoneResourcesList result =
        landingZoneApiDispatch.listAzureLandingZoneResources(
            bearerTokenFactory.from(request), landingZoneId);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiDeleteAzureLandingZoneResult> deleteAzureLandingZone(
      @PathVariable("landingZoneId") UUID landingZoneId,
      @RequestBody ApiDeleteAzureLandingZoneRequestBody body) {
    String resultEndpoint =
        String.format(
            "%s/%s/%s", request.getServletPath(), "delete-result", body.getJobControl().getId());
    ApiDeleteAzureLandingZoneResult result =
        landingZoneApiDispatch.deleteLandingZone(
            bearerTokenFactory.from(request), landingZoneId, body, resultEndpoint);
    return new ResponseEntity<>(result, getAsyncResponseCode(result.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiDeleteAzureLandingZoneJobResult> getDeleteAzureLandingZoneResult(
      UUID landingZoneId, String jobId) {
    ApiDeleteAzureLandingZoneJobResult response =
        landingZoneApiDispatch.getDeleteAzureLandingZoneResult(
            bearerTokenFactory.from(request), landingZoneId, jobId);
    return new ResponseEntity<>(response, getAsyncResponseCode(response.getJobReport()));
  }

  @Override
  public ResponseEntity<ApiAzureLandingZone> getAzureLandingZone(
      @PathVariable("landingZoneId") UUID landingZoneId) {
    ApiAzureLandingZone result =
        landingZoneApiDispatch.getAzureLandingZone(bearerTokenFactory.from(request), landingZoneId);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiAzureLandingZoneList> listAzureLandingZones(
      @Valid @RequestParam(value = "billingProfileId", required = false) UUID billingProfileId) {
    ApiAzureLandingZoneList result =
        landingZoneApiDispatch.listAzureLandingZones(
            bearerTokenFactory.from(request), billingProfileId);
    return new ResponseEntity<>(result, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiResourceQuota> getResourceQuotaResult(
      @PathVariable("landingZoneId") UUID landingZoneId,
      @Valid @RequestParam(value = "azureResourceId", required = true) String azureResourceId) {

    ApiResourceQuota result =
        landingZoneApiDispatch.getResourceQuota(
            bearerTokenFactory.from(request), landingZoneId, azureResourceId);

    return new ResponseEntity<>(result, HttpStatus.OK);
  }
}
