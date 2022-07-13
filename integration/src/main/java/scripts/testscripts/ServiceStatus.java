package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.UnauthenticatedApi;
import bio.terra.workspace.client.ApiClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.ParameterKeys;
import scripts.utils.ParameterUtils;

public class ServiceStatus extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(ServiceStatus.class);
  private Duration delay = Duration.ZERO;
  private boolean flaky = true;

  @Override
  public void setParametersMap(Map<String, String> parametersMap) {
    delay =
        Duration.ofSeconds(
            Long.parseLong(
                ParameterUtils.getParamOrThrow(
                    parametersMap, ParameterKeys.STATUS_CHECK_DELAY_PARAMETER)));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    if (delay.getSeconds() > 0) TimeUnit.SECONDS.sleep(delay.getSeconds());

    logger.info("Checking service status endpoint now.");
    ApiClient apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
    UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
    unauthenticatedApi.serviceStatus();
    if (flaky) {
      TimeUnit.MICROSECONDS.sleep(1000);
      flaky = !flaky;
      throw new Exception("Service endpoint timeout");
    }

    int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
    logger.info("Service status return code: {}", httpCode);
  }
}
