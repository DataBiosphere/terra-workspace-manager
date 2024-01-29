package bio.terra.workspace.service.datarepo;

import bio.terra.common.exception.ValidationException;
import bio.terra.common.tracing.JakartaTracingFilter;
import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.datarepo.model.SnapshotRetrieveIncludeModel;
import bio.terra.workspace.app.configuration.external.DataRepoConfiguration;
import bio.terra.workspace.service.datarepo.exception.DataRepoInternalServerErrorException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.ws.rs.client.Client;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataRepoService {

  private final DataRepoConfiguration dataRepoConfiguration;
  private final Client commonHttpClient;

  @Autowired
  public DataRepoService(DataRepoConfiguration dataRepoConfiguration, OpenTelemetry openTelemetry) {
    this.dataRepoConfiguration = dataRepoConfiguration;
    commonHttpClient =
        new ApiClient().getHttpClient().register(new JakartaTracingFilter(openTelemetry));
  }

  private final Logger logger = LoggerFactory.getLogger(DataRepoService.class);

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient().setHttpClient(commonHttpClient);
    client.setAccessToken(accessToken);
    return client;
  }

  private RepositoryApi repositoryApi(String instanceName, AuthenticatedUserRequest userRequest) {
    String instanceUrl = getInstanceUrl(instanceName);
    return new RepositoryApi(getApiClient(userRequest.getRequiredToken()).setBasePath(instanceUrl));
  }

  public String getInstanceUrl(String instanceName) {
    HashMap<String, String> dataRepoInstances = dataRepoConfiguration.getInstances();
    String cleanedInstanceName = instanceName.toLowerCase().trim();

    if (dataRepoInstances.containsKey(cleanedInstanceName)) {
      return dataRepoInstances.get(cleanedInstanceName);
    } else {
      throw new ValidationException(
          "Provided Data repository instance is not allowed. Valid instances are: \""
              + String.join("\", \"", dataRepoInstances.keySet())
              + "\"");
    }
  }

  /**
   * Returns whether or not a given snapshot is readable for a given user. On the TDR side,
   * retrieveSnapshot requires that a user have read access to the snapshot's data.
   */
  @WithSpan
  public boolean snapshotReadable(
      String instanceName, String snapshotId, AuthenticatedUserRequest userRequest) {
    RepositoryApi repositoryApi = repositoryApi(instanceName, userRequest);

    try {
      repositoryApi.retrieveSnapshot(
          UUID.fromString(snapshotId), List.of(SnapshotRetrieveIncludeModel.NONE));
      logger.info("Retrieved snapshotId {} on Data Repo instance {}", snapshotId, instanceName);
      return true;
    } catch (ApiException e) {
      if (e.getCode() == HttpStatus.NOT_FOUND.value()
          || e.getCode() == HttpStatus.FORBIDDEN.value()) {
        return false;
      } else {
        throw new DataRepoInternalServerErrorException(
            "Data Repo returned the following unexpected error: " + e.getMessage(), e.getCause());
      }
    } catch (IllegalArgumentException e) {
      logger.info("Invalid snapshotId {} on Data Repo instance {}", snapshotId, instanceName, e);
      // this can't exist so return false
      return false;
    }
  }
}
