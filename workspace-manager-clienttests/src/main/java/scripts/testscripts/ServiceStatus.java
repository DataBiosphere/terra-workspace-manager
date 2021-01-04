package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.UnauthenticatedApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.SystemStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.WorkspaceManagerServiceUtils;

public class ServiceStatus extends TestScript {
    private static final Logger logger = LoggerFactory.getLogger(ServiceStatus.class);

    @Override
    public void userJourney(TestUserSpecification testUser) throws Exception {
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClientWithoutAccessToken(server);
        UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
        SystemStatus systemStatus = unauthenticatedApi.serviceStatus();

        int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
        logger.info("Service status with code {}: {}", httpCode, systemStatus);
    }
}
