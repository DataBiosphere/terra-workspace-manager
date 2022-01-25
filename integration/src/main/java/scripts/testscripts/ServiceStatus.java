package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.UnauthenticatedApi;
import bio.terra.workspace.client.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.ParameterKeys;

public class ServiceStatus extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(ServiceStatus.class);
  private Duration delay = Duration.ZERO;

  @Override
  public void setParameters(Map<String, String> parameters) {

    if (parameters == null || !parameters.containsKey(ParameterKeys.STATUS_CHECK_DELAY_PARAMETER)) {
      return;
    }
    delay = Duration.ofSeconds(Long.parseLong(parameters.get(ParameterKeys.STATUS_CHECK_DELAY_PARAMETER)));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    if (delay.getSeconds() > 0) TimeUnit.SECONDS.sleep(delay.getSeconds());

    logger.info("Checking service status endpoint now.");
    ApiClient apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
    UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
    unauthenticatedApi.serviceStatus();
    int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
    logger.info("Service status return code: {}", httpCode);
  }
}
