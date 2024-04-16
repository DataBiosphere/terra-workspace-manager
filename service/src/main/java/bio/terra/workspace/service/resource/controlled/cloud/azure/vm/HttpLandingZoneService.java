package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.tracing.JakartaTracingFilter;
import bio.terra.landingzone.library.landingzones.deployment.LandingZonePurpose;
import bio.terra.lz.futureservice.api.LandingZonesApi;

import bio.terra.lz.futureservice.client.ApiClient;
import bio.terra.lz.futureservice.client.ApiException;
import bio.terra.lz.futureservice.model.CreateAzureLandingZoneRequestBody;
import bio.terra.lz.futureservice.model.JobControl;
import bio.terra.workspace.amalgam.landingzone.azure.LandingApiClientTypeAdapter;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneServiceApiException;
import bio.terra.workspace.amalgam.landingzone.azure.WorkspaceLandingZoneService;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDefinitionList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiCreateLandingZoneResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneJobResult;
import bio.terra.workspace.generated.model.ApiDeleteAzureLandingZoneResult;
import bio.terra.workspace.generated.model.ApiResourceQuota;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.ws.rs.client.Client;

import java.util.List;
import java.util.UUID;

public class HttpLandingZoneService implements WorkspaceLandingZoneService {
    private final Client commonHttpClient;
    private final LandingApiClientTypeAdapter typeAdapter;

    public HttpLandingZoneService(OpenTelemetry openTelemetry) {
        this.commonHttpClient =
                new bio.terra.profile.client.ApiClient().getHttpClient().register(new JakartaTracingFilter(openTelemetry));
        this.typeAdapter = new LandingApiClientTypeAdapter();
    }

    private ApiClient getApiClient(String accessToken) {
        ApiClient apiClient = new ApiClient().setHttpClient(commonHttpClient);
        apiClient.setAccessToken(accessToken);
        apiClient.setBasePath("todo");
        return apiClient;
    }

    private LandingZonesApi getLandingZonesApi(BearerToken bearerToken) {
        return new LandingZonesApi(getApiClient(bearerToken.getToken()));
    }

    @Override
    public ApiCreateLandingZoneResult startLandingZoneCreationJob(BearerToken bearerToken, String jobId, UUID landingZoneId, String definition, String version, List<ApiAzureLandingZoneParameter> parameters, UUID billingProfileId, String asyncResultEndpoint) throws InterruptedException {
        var client = getLandingZonesApi(bearerToken);

        var body = new CreateAzureLandingZoneRequestBody()
                .landingZoneId(landingZoneId)
                        .billingProfileId(billingProfileId)
                                .definition(definition)
                                        .parameters(null) // TODO
                                                .jobControl(new JobControl()
                                                        .id(jobId))
                                                        .version(version);
        try {
            var result =
                    LzsRetry.retry( () -> client.createAzureLandingZone(
                    body));
            return typeAdapter.toApiCreateLandingZoneResult(result);
        } catch (ApiException e) {
            throw convertApiException(e);
        }

    }

    private RuntimeException convertApiException(ApiException ex) {
        // todo
        throw new LandingZoneServiceApiException("whoops");//ex);
//        if (ex.getCode() == HttpStatus.UNAUTHORIZED.value()) {
//            return new PolicyServiceAuthorizationException(
//                    "Not authorized to access Terra Landing Zone Service", ex.getCause());
//        } else if (ex.getCode() == HttpStatus.NOT_FOUND.value()) {
//            return new PolicyServiceNotFoundException("Landing Zone Service returns not found exception", ex);
//        } else if (ex.getCode() == HttpStatus.BAD_REQUEST.value()
//                && StringUtils.containsIgnoreCase(ex.getMessage(), "duplicate")) {
//            return new PolicyServiceDuplicateException(
//                    "Landing Zone service throws duplicate object exception", ex);
//        } else if (ex.getCode() == HttpStatus.CONFLICT.value()) {
//            return new PolicyConflictException("Landing Zone service throws conflict exception", ex);
//        } else {
//            return new Landi(ex);
//        }
    }

    @Override
    public ApiDeleteAzureLandingZoneResult startLandingZoneDeletionJob(BearerToken bearerToken, String jobId, UUID landingZoneId, String resultEndpoint) {
        return null;
    }

    @Override
    public ApiDeleteAzureLandingZoneJobResult getDeleteLandingZoneResult(BearerToken bearerToken, UUID landingZoneId, String jobId) {
        return null;
    }

    @Override
    public ApiAzureLandingZoneResult getAsyncJobResult(BearerToken bearerToken, String jobId) {
        return null;
    }

    @Override
    public ApiAzureLandingZone getAzureLandingZone(BearerToken bearerToken, UUID landingZoneId) {
        return null;
    }

    @Override
    public ApiAzureLandingZoneList listLandingZonesByBillingProfile(BearerToken bearerToken, UUID billingProfileId) {
        return null;
    }

    @Override
    public ApiAzureLandingZoneList listLandingZones(BearerToken bearerToken) {
        return null;
    }

    @Override
    public String getLandingZoneRegion(BearerToken bearerToken, UUID landingZoneId) {
        return "";
    }

    @Override
    public ApiAzureLandingZoneDefinitionList listLandingZoneDefinitions(BearerToken bearerToken) {
        return null;
    }

    @Override
    public ApiAzureLandingZoneResourcesList listResourcesWithPurposes(BearerToken bearerToken, UUID landingZoneId) {
        return null;
    }

    @Override
    public ApiAzureLandingZoneResourcesList listResourcesMatchingPurpose(BearerToken bearerToken, UUID landingZoneId, LandingZonePurpose resourcePurpose) {
        return null;
    }

    @Override
    public ApiResourceQuota getResourceQuota(BearerToken bearerToken, UUID landingZoneId, String resourceId) {
        return null;
    }
}
