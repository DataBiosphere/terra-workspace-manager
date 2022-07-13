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
  private ThreadLocal<Boolean> flaky = ThreadLocal.withInitial(() -> true);
  private ThreadLocal<Integer> flaky2 = ThreadLocal.withInitial(() -> 0);
  private ThreadLocal<Integer> maxAttempts = ThreadLocal.withInitial(() -> 2);

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
    flaky2.set(flaky2.get()+1);
    logger.info("Flaky=" + flaky.get());
    logger.info("Flaky2=" + flaky2.get());
    logger.info("exceeded: {}", flaky2.get()>maxAttempts.get());
    if (flaky.get()) {
      TimeUnit.MICROSECONDS.sleep(1000);
      flaky.set(!flaky.get());
      throw new Exception("Service endpoint timeout");
    }

    int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
    logger.info("Service status return code: {}", httpCode);
  }
}
