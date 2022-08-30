package bio.terra.workspace.service.datarepo;

import bio.terra.common.exception.ValidationException;
import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.workspace.app.configuration.external.DataRepoConfiguration;
import bio.terra.workspace.service.datarepo.exception.DataRepoInternalServerErrorException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.HashMap;
import jakarta.ws.rs.client.Client;
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
  public DataRepoService(DataRepoConfiguration dataRepoConfiguration) {
    this.dataRepoConfiguration = dataRepoConfiguration;
    commonHttpClient = new ApiClient().getHttpClient();
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
  @Traced
  public boolean snapshotReadable(
      String instanceName, String snapshotId, AuthenticatedUserRequest userRequest) {
    RepositoryApi repositoryApi = repositoryApi(instanceName, userRequest);

    try {
      repositoryApi.retrieveSnapshot(snapshotId);
      logger.info("Retrieved snapshotId {} on Data Repo instance {}", snapshotId, instanceName);
      return true;
    } catch (ApiException e) {
      // TDR uses 401 (rather than 403) to indicate "user does not have permission", so we check for
      // UNAUTHORIZED here instead of FORBIDDEN.
      if (e.getCode() == HttpStatus.NOT_FOUND.value()
          || e.getCode() == HttpStatus.UNAUTHORIZED.value()) {
        return false;
      } else {
        throw new DataRepoInternalServerErrorException(
            "Data Repo returned the following error: " + e.getMessage(), e.getCause());
      }
    }
  }
}
