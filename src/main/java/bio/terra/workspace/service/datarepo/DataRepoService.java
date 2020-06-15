package bio.terra.workspace.service.datarepo;

import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.workspace.app.configuration.DataRepoConfig;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataRepoService {

  private final DataRepoConfig dataRepoConfig;

  @Autowired
  public DataRepoService(DataRepoConfig dataRepoConfig) {
    this.dataRepoConfig = dataRepoConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client;
  }

  private RepositoryApi repositoryApi(String instanceName, AuthenticatedUserRequest userReq) {
    String instanceUrl = getInstanceUrl(instanceName);
    return new RepositoryApi(getApiClient(userReq.getRequiredToken()).setBasePath(instanceUrl));
  }

  public String getInstanceUrl(String instanceName) {
    HashMap<String, String> dataRepoInstances = dataRepoConfig.getInstances();
    String cleanedInstanceName = instanceName.toLowerCase().trim();

    if (dataRepoInstances.containsKey(cleanedInstanceName)) {
      return dataRepoInstances.get(cleanedInstanceName);
    } else {
      throw new ValidationException(
          "Data repository instance \""
              + cleanedInstanceName
              + "\" is not allowed. Valid instances are: \""
              + String.join("\", \"", dataRepoInstances.keySet())
              + "\"");
    }
  }

  public boolean snapshotExists(
      String instanceName, String snapshotId, AuthenticatedUserRequest userReq) {
    RepositoryApi repositoryApi = repositoryApi(instanceName, userReq);

    try {
      repositoryApi.retrieveSnapshot(snapshotId);
      return true;
    } catch (ApiException e) {
      return false;
    }
  }
}
