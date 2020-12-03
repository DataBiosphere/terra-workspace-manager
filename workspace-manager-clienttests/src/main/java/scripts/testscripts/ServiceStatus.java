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

    /** Public constructor so that this class can be instantiated via reflection. */
    public ServiceStatus() {
        super();
    }

    public void userJourney(TestUserSpecification testUser) throws Exception {
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClient(server);
        UnauthenticatedApi unauthenticatedApi = new UnauthenticatedApi(apiClient);
        SystemStatus systemStatus = unauthenticatedApi.serviceStatus();
        logger.info("systemStatus: {}", systemStatus);

        int httpCode = unauthenticatedApi.getApiClient().getStatusCode();
        logger.info("Service status HTTP code: {}", httpCode);
    }
}
