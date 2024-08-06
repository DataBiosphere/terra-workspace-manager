package bio.terra.workspace.service.buffer;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.api.UnauthenticatedApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.logging.RequestIdFilter;
import bio.terra.common.tracing.JakartaTracingFilter;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.service.buffer.exception.BufferServiceAPIException;
import bio.terra.workspace.service.buffer.exception.BufferServiceAuthorizationException;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** A service for integrating with the Resource Buffer Service. */
@Service
public class BufferService {
  private final Logger logger = LoggerFactory.getLogger(BufferService.class);

  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final Client commonHttpClient;

  @Autowired
  public BufferService(
      BufferServiceConfiguration bufferServiceConfiguration, OpenTelemetry openTelemetry) {
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.commonHttpClient =
        new ApiClient().getHttpClient().register(new JakartaTracingFilter(openTelemetry));
  }

  /**
   * Verify that the Buffer Service configuration is valid.
   *
   * @throws InternalServerErrorException if the application is misconfigured
   */
  public void verifyConfiguration() {
    this.bufferServiceConfiguration.getAccessToken();
  }

  /**
   * Check the status of the Buffer Service.
   *
   * @return true if the service is up and running
   */
  public boolean status() {
    try {
      var unauthenticatedApi =
          new UnauthenticatedApi(
              new ApiClient()
                  .setHttpClient(commonHttpClient)
                  .setBasePath(bufferServiceConfiguration.getInstanceUrl()));
      var result = unauthenticatedApi.serviceStatus();
      return result.isOk();
    } catch (ApiException e) {
      logger.error("Error querying Buffer API status", e);
      return false;
    }
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client =
        new ApiClient()
            .setHttpClient(commonHttpClient)
            .addDefaultHeader(
                RequestIdFilter.REQUEST_ID_HEADER, MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY));
    client.setAccessToken(accessToken);
    return client;
  }

  private BufferApi bufferApi(String instanceUrl) {
    return new BufferApi(
        getApiClient(bufferServiceConfiguration.getAccessToken()).setBasePath(instanceUrl));
  }

  /**
   * Retrieve a single resource from the Buffer Service. The instance and pool are already
   * configured.
   *
   * @param requestBody requestBody
   * @return ResourceInfo
   */
  public ResourceInfo handoutResource(HandoutRequestBody requestBody) {
    try {
      BufferApi bufferApi = bufferApi(bufferServiceConfiguration.getInstanceUrl());
      ResourceInfo info =
          bufferApi.handoutResource(requestBody, bufferServiceConfiguration.getPoolId());
      logger.info(
          "Retrieved resource from pool {} on Buffer Service instance {}",
          bufferServiceConfiguration.getPoolId(),
          bufferServiceConfiguration.getInstanceUrl());
      return info;
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        throw new BufferServiceAuthorizationException(
            "Not authorized to access Buffer Service", e.getCause());
      } else {
        throw new BufferServiceAPIException(e);
      }
    }
  }
}
