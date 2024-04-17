package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ConflictException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.tracing.JakartaTracingFilter;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.lz.futureservice.api.LandingZonesApi;
import bio.terra.lz.futureservice.client.ApiClient;
import bio.terra.lz.futureservice.client.ApiException;
import bio.terra.lz.futureservice.model.CreateAzureLandingZoneRequestBody;
import bio.terra.lz.futureservice.model.DeleteAzureLandingZoneRequestBody;
import bio.terra.lz.futureservice.model.JobControl;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import bio.terra.workspace.common.utils.MapperUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinition;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.LzsRetry;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.ws.rs.client.Client;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

public class HttpLandingZoneService implements WorkspaceLandingZoneService {
  private final Client commonHttpClient;
  private final LandingZoneServiceConfiguration config;
  private final LandingApiClientTypeAdapter typeAdapter;

  public HttpLandingZoneService(
      OpenTelemetry openTelemetry, LandingZoneServiceConfiguration config) {
    this.commonHttpClient =
        new ApiClient().getHttpClient().register(new JakartaTracingFilter(openTelemetry));
    this.config = config;
    this.typeAdapter = new LandingApiClientTypeAdapter();
  }

  @Override
  public ApiCreateLandingZoneResult startLandingZoneCreationJob(
      BearerToken bearerToken,
      String jobId,
      UUID landingZoneId,
      String definition,
      String version,
      List<ApiAzureLandingZoneParameter> parameters,
      UUID billingProfileId,
      String asyncResultEndpoint)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);

    var body =
        new CreateAzureLandingZoneRequestBody()
            .landingZoneId(landingZoneId)
            .billingProfileId(billingProfileId)
            .definition(definition)
            .parameters(
                MapperUtils.LandingZoneMapper.apiClientLandingZoneParametersFrom(parameters))
            .jobControl(new JobControl().id(jobId))
            .version(version);
    try {
      var result = LzsRetry.retry(() -> client.createAzureLandingZone(body));
      return typeAdapter.toApiCreateLandingZoneResultFromApiClient(result);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiDeleteAzureLandingZoneResult startLandingZoneDeletionJob(
      BearerToken bearerToken, String jobId, UUID landingZoneId, String resultEndpoint)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);
    var body = new DeleteAzureLandingZoneRequestBody().jobControl(new JobControl().id(jobId));
    try {
      var result = LzsRetry.retry(() -> client.deleteAzureLandingZone(body, landingZoneId));
      return typeAdapter.toApiDeleteAzureLandingZoneResultFromApiClient(result);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiDeleteAzureLandingZoneJobResult getDeleteLandingZoneResult(
      BearerToken bearerToken, UUID landingZoneId, String jobId) throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);
    try {
      var result =
          LzsRetry.retry(() -> client.getDeleteAzureLandingZoneResult(landingZoneId, jobId));
      return typeAdapter.toApiDeleteAzureLandingZoneJobResultFromApiClient(result);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZoneResult getAsyncJobResult(BearerToken bearerToken, String jobId)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);
    try {
      var result = LzsRetry.retry(() -> client.getCreateAzureLandingZoneResult(jobId));
      return typeAdapter.toApiAzureLandingZoneResultFromApiClient(result);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);
    try {

      var result = LzsRetry.retry(() -> client.getAzureLandingZone(landingZoneId));
      return typeAdapter.toApiAzureLandingZone(result);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZoneList listLandingZonesByBillingProfile(
      BearerToken bearerToken, UUID billingProfileId) throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);
    ApiAzureLandingZoneList result = new ApiAzureLandingZoneList();

    try {
      var response = LzsRetry.retry(() -> client.listAzureLandingZones(billingProfileId));
      var landingZones = response.getLandingzones();
      if (!landingZones.isEmpty()) {
        if (landingZones.size() == 1) {
          result.addLandingzonesItem(typeAdapter.toApiAzureLandingZone(landingZones.get(0)));
        }
      } else {
        throw new ConflictException(
            String.format(
                "There are more than one landing zone found for the given billing profile: '%s'. Please"
                    + " check the landing zone deployment is correct.",
                billingProfileId));
      }

    } catch (ApiException e) {
      throw convertApiException(e);
    }

    return result;
  }

  @Override
  public ApiAzureLandingZoneList listLandingZones(BearerToken bearerToken) {
    throw new RuntimeException("todo");
  }

  @Override
  public String getLandingZoneRegion(BearerToken bearerToken, UUID landingZoneId)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);

    try {
      var response = LzsRetry.retry(() -> client.getAzureLandingZone(landingZoneId));
      return response.getRegion();
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZoneDefinitionList listLandingZoneDefinitions(BearerToken bearerToken)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);

    try {
      var response = LzsRetry.retry(client::listAzureLandingZonesDefinitions);
      return new ApiAzureLandingZoneDefinitionList()
          .landingzones(
              response.getLandingzones().stream()
                  .map(
                      t ->
                          new ApiAzureLandingZoneDefinition()
                              .definition(t.getDefinition())
                              .name(t.getName())
                              .description(t.getDescription())
                              .version(t.getVersion()))
                  .toList());
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZoneResourcesList listResourcesWithPurposes(
      BearerToken bearerToken, UUID landingZoneId) throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);

    try {
      var response = LzsRetry.retry(() -> client.listAzureLandingZoneResources(landingZoneId));
      return typeAdapter.toApiResourcesList(response);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiAzureLandingZoneResourcesList listResourcesMatchingPurpose(
      BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose)
      throws InterruptedException {
    var client = getLandingZonesApi(bearerToken);

    try {
      var response = LzsRetry.retry(() -> client.listAzureLandingZoneResources(landingZoneId));
      var filtered =
          response.getResources().stream()
              .filter(group -> group.getPurpose().equalsIgnoreCase(resourcePurpose.toString()));
      return typeAdapter.toApiResourcesListFromApiClient(filtered.toList(), landingZoneId);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  @Override
  public ApiResourceQuota getResourceQuota(
      BearerToken bearerToken, UUID landingZoneId, String resourceId) {
    var client = getLandingZonesApi(bearerToken);

    try {
      var response = client.getResourceQuotaResult(landingZoneId, resourceId);
      return typeAdapter.toApiResourceQuotaFromApiClient(landingZoneId, response);
    } catch (ApiException e) {
      throw convertApiException(e);
    }
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = new ApiClient().setHttpClient(commonHttpClient);
    apiClient.setAccessToken(accessToken);
    apiClient.setBasePath(config.getBasePath());
    return apiClient;
  }

  private LandingZonesApi getLandingZonesApi(BearerToken bearerToken) {
    return new LandingZonesApi(getApiClient(bearerToken.getToken()));
  }

  private RuntimeException convertApiException(ApiException ex) {
    if (ex.getCode() == HttpStatus.UNAUTHORIZED.value()) {
      return new LandingZoneServiceAuthorizationException(
          "Not authorized to access Terra Landing Zone Service", ex.getCause());
    } else if (ex.getCode() == HttpStatus.NOT_FOUND.value()) {
      return new LandingZoneServiceNotFoundException(
          "Landing Zone Service returns not found exception", ex);
    } else if (ex.getCode() == HttpStatus.BAD_REQUEST.value()
        && StringUtils.containsIgnoreCase(ex.getMessage(), "duplicate")) {
      return new LandingZoneServiceDuplicateException(
          "Landing Zone service throws duplicate object exception", ex);
    } else if (ex.getCode() == HttpStatus.CONFLICT.value()) {
      return new LandingZoneServiceConflictException(
          "Landing Zone service throws conflict exception", ex);
    } else {
      return new LandingZoneServiceApiException(ex);
    }
  }
}
