package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.UnauthenticatedApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServiceStatus extends TestScript {
    private static final Logger logger = LoggerFactory.getLogger(ServiceStatus.class);
    private int delayBySECONDS = 0;

    @Override
    public void setParameters(List<String> parameters) {

        if (parameters == null || parameters.size() == 0) {
            return;
        }
        delayBySECONDS = Integer.parseInt(parameters.get(0));
    }

    @Override
    public void userJourney(TestUserSpecification testUser) throws Exception {
        if (delayBySECONDS > 0) TimeUnit.SECONDS.sleep(delayBySECONDS);

        logger.info("Starting test script");
        ApiClient apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
        UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
        SystemStatus systemStatus = unauthenticatedApi.serviceStatus();

        int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
        logger.info("Service status with code {}: {}", httpCode, systemStatus);
    }
}
