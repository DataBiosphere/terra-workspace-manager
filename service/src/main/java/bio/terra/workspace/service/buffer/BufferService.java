package bio.terra.workspace.service.buffer;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.common.logging.RequestIdFilter;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.service.buffer.exception.BufferServiceAPIException;
import bio.terra.workspace.service.buffer.exception.BufferServiceAuthorizationException;
import io.opencensus.contrib.http.jaxrs.JaxrsClientExtractor;
import io.opencensus.contrib.http.jaxrs.JaxrsClientFilter;
import io.opencensus.trace.Tracing;
import java.io.IOException;
import jakarta.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** A service for integrating with the Resource Buffer Service. */
@Component
public class BufferService {
  private final Logger logger = LoggerFactory.getLogger(BufferService.class);

  private final BufferServiceConfiguration bufferServiceConfiguration;
  private final Client commonHttpClient;

  @Autowired
  public BufferService(BufferServiceConfiguration bufferServiceConfiguration) {
    this.bufferServiceConfiguration = bufferServiceConfiguration;
    this.commonHttpClient =
        new ApiClient()
            .getHttpClient()
            .register(
                new JaxrsClientFilter(
                    new JaxrsClientExtractor(), Tracing.getPropagationComponent().getB3Format()));
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

  private BufferApi bufferApi(String instanceUrl) throws IOException {
    return new BufferApi(
        getApiClient(bufferServiceConfiguration.getAccessToken()).setBasePath(instanceUrl));
  }

  /**
   * Retrieve a single resource from the Buffer Service. The instance and pool are already
   * configured.
   *
   * @param requestBody
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
    } catch (IOException e) {
      throw new BufferServiceAuthorizationException(
          String.format(
              "Error reading or parsing credentials file at %s",
              bufferServiceConfiguration.getClientCredentialFilePath()),
          e.getCause());
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
