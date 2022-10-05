package bio.terra.workspace.service.spendprofile.client;

import bio.terra.profile.api.ProfileApi;
import bio.terra.profile.client.ApiClient;
import bio.terra.workspace.app.configuration.external.SpendProfileConfiguration;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import io.opencensus.contrib.http.jaxrs.JaxrsClientExtractor;
import io.opencensus.contrib.http.jaxrs.JaxrsClientFilter;
import io.opencensus.trace.Tracing;
import javax.ws.rs.client.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpBpmClientProvider implements BpmClientProvider {

  private final String basePath;
  private final Client commonHttpClient;

  @Autowired
  public HttpBpmClientProvider(SpendProfileConfiguration config) {
    this(config.getBasePath());
  }

  public HttpBpmClientProvider(String basePath) {
    this.basePath = basePath;
    this.commonHttpClient =
        new ApiClient()
            .getHttpClient()
            .register(
                new JaxrsClientFilter(
                    new JaxrsClientExtractor(), Tracing.getPropagationComponent().getB3Format()));
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = new ApiClient().setHttpClient(commonHttpClient);
    apiClient.setAccessToken(accessToken);
    apiClient.setBasePath(basePath);
    return apiClient;
  }

  public ProfileApi getProfileApi(AuthenticatedUserRequest userRequest) {
    return new ProfileApi(getApiClient(userRequest.getRequiredToken()));
  }
}
