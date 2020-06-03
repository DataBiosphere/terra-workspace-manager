package bio.terra.workspace.service.datarepo;

import bio.terra.datarepo.api.RepositoryApi;
import bio.terra.datarepo.client.ApiClient;
import bio.terra.datarepo.client.ApiException;
import bio.terra.workspace.common.exception.ValidationException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class DataRepoService {

  private static final Set<String> INSTANCE_WHITELIST =
      Stream.of(
              "https://jade.datarepo-dev.broadinstitute.org",
              "https://jade-terra.datarepo-prod.broadinstitute.org",
              "https://data.terra.bio")
          .collect(Collectors.toSet());

  private ApiClient getApiClient(String accessToken) {
    ApiClient client = new ApiClient();
    client.setAccessToken(accessToken);
    return client;
  }

  private RepositoryApi repositoryApi(String instance, AuthenticatedUserRequest userReq) {
    if (INSTANCE_WHITELIST.contains(instance)) {
      return new RepositoryApi(getApiClient(userReq.getRequiredToken()).setBasePath(instance));
    } else {
      throw new ValidationException("Data repository instance " + instance + " is not allowed.");
    }
  }

  public boolean snapshotExists(
      String instance, String snapshotId, AuthenticatedUserRequest userReq) {
    RepositoryApi repositoryApi = repositoryApi(instance, userReq);

    try {
      repositoryApi.retrieveSnapshot(snapshotId);
      return true;
    } catch (ApiException e) {
      return false;
    }
  }
}
