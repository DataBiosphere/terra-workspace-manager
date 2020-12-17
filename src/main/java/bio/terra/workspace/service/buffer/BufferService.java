package bio.terra.workspace.service.buffer;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.app.configuration.spring.TraceInterceptorConfig;
import bio.terra.workspace.service.buffer.exception.BufferServiceAuthorizationException;
import bio.terra.workspace.service.buffer.exception.BufferServiceInternalServerErrorException;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
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

  @Autowired
  public BufferService(BufferServiceConfiguration bufferServiceConfiguration) {
    this.bufferServiceConfiguration = bufferServiceConfiguration;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.addDefaultHeader(
        TraceInterceptorConfig.MDC_REQUEST_ID_HEADER,
        MDC.get(TraceInterceptorConfig.MDC_REQUEST_ID_KEY));
    client.setAccessToken(accessToken);
    return client;
  }

  private BufferApi bufferApi(String instanceUrl) throws IOException {
    return new BufferApi(
        getApiClient(bufferServiceConfiguration.getAccessToken()).setBasePath(instanceUrl));
  }

  @Traced
  public PoolInfo getPoolInfo() {
    try {
      BufferApi bufferApi = bufferApi(bufferServiceConfiguration.getInstanceUrl());
      PoolInfo info = bufferApi.getPoolInfo(bufferServiceConfiguration.getPoolId());
      logger.info(
          String.format(
              "Retrieved pool %s on Buffer Service instance %s",
              bufferServiceConfiguration.getPoolId(), bufferServiceConfiguration.getInstanceUrl()));
      return info;
    } catch (IOException e) {
      throw new BufferServiceAuthorizationException(
          "Error reading or parsing credentials file", e.getCause());
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        throw new BufferServiceAuthorizationException(
            "Not authorized to access Buffer Service", e.getCause());
      } else {
        throw new BufferServiceInternalServerErrorException(
            "Buffer Service returned the following error: " + e.getMessage(), e.getCause());
      }
    }
  }

  public ResourceInfo handoutResource(HandoutRequestBody requestBody) {
    try {
      BufferApi bufferApi = bufferApi(bufferServiceConfiguration.getInstanceUrl());
      ResourceInfo info =
          bufferApi.handoutResource(requestBody, bufferServiceConfiguration.getPoolId());
      logger.info(
          String.format(
              "Retrieved resource from pool %s on Buffer Service instance %s",
              bufferServiceConfiguration.getPoolId(), bufferServiceConfiguration.getInstanceUrl()));
      return info;
    } catch (IOException e) {
      throw new BufferServiceAuthorizationException(
          "Could not find credentials file", e.getCause());
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
        return null;
      } else if (e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        throw new BufferServiceAuthorizationException(
            "Not authorized to access Buffer Service", e.getCause());
      } else {
        throw new BufferServiceInternalServerErrorException(
            "Buffer Service returned the following error: " + e.getMessage(), e.getCause());
      }
    }
  }
}
