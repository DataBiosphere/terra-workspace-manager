package bio.terra.workspace.service.buffer;

import bio.terra.buffer.api.BufferApi;
import bio.terra.buffer.api.UnauthenticatedApi;
import bio.terra.buffer.client.ApiClient;
import bio.terra.buffer.client.ApiException;
import bio.terra.buffer.model.HandoutRequestBody;
import bio.terra.buffer.model.PoolInfo;
import bio.terra.buffer.model.ResourceInfo;
import bio.terra.buffer.model.SystemStatus;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.generated.model.ApiSystemStatusSystems;
import bio.terra.workspace.service.buffer.exception.BufferServiceAPIException;
import bio.terra.workspace.service.buffer.exception.BufferServiceAuthorizationException;
import io.opencensus.contrib.spring.aop.Traced;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    client.setAccessToken(accessToken);
    return client;
  }

  private BufferApi bufferApi(String instanceUrl) throws IOException {
    return new BufferApi(
        getApiClient(bufferServiceConfiguration.getAccessToken()).setBasePath(instanceUrl));
  }

  /**
   * Return the PoolInfo object for the ResourceBuffer pool that we are using to create Google Cloud
   * projects. Note that this is configured once per Workspace Manager instance (both the instance
   * of RBS to use and which pool) so no configuration happens here.
   *
   * @return PoolInfo
   */
  @Traced
  public PoolInfo getPoolInfo() {
    try {
      BufferApi bufferApi = bufferApi(bufferServiceConfiguration.getInstanceUrl());
      PoolInfo info = bufferApi.getPoolInfo(bufferServiceConfiguration.getPoolId());
      logger.info(
          "Retrieved pool {} on Buffer Service instance {}",
          bufferServiceConfiguration.getPoolId(),
          bufferServiceConfiguration.getInstanceUrl());
      return info;
    } catch (IOException e) {
      throw new BufferServiceAuthorizationException(
          "Error reading or parsing credentials file", e.getCause());
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        throw new BufferServiceAuthorizationException(
            "Not authorized to access Buffer Service", e.getCause());
      } else {
        throw new BufferServiceAPIException(e);
      }
    }
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
          "Error reading or parsing credentials file", e.getCause());
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        throw new BufferServiceAuthorizationException(
            "Not authorized to access Buffer Service", e.getCause());
      } else {
        throw new BufferServiceAPIException(e);
      }
    }
  }

  public ApiSystemStatusSystems status() {
    UnauthenticatedApi unauthenticatedApi =
        new UnauthenticatedApi(
            getApiClient(null).setBasePath(bufferServiceConfiguration.getInstanceUrl()));
    try {
      SystemStatus status = unauthenticatedApi.serviceStatus();
      Map<String, bio.terra.buffer.model.SystemStatusSystems> subsystemStatusMap =
          status.getSystems();
      List<String> subsystemStatusMessages =
          subsystemStatusMap.entrySet().stream()
              .map(
                  (entry) ->
                      entry.getKey() + ": " + StringUtils.join(entry.getValue().getMessages()))
              .collect(Collectors.toList());
      return new ApiSystemStatusSystems().ok(status.isOk()).messages(subsystemStatusMessages);
    } catch (ApiException e) {
      return new ApiSystemStatusSystems().ok(false).addMessagesItem(e.getResponseBody());
    }
  }
}
