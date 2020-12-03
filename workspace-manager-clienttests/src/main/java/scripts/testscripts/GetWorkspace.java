package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.WorkspaceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.WorkspaceManagerServiceUtils;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GetWorkspace extends TestScript {
    private static final Logger logger = LoggerFactory.getLogger(GetWorkspace.class);

    /** Public constructor so that this class can be instantiated via reflection. */
    public GetWorkspace() {
        super();
    }

    @Override
    public void userJourney(TestUserSpecification testUser) throws Exception {
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUser, server);
        WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);

        /**
         * This GetWorkspace test expects a bogus workspace id resulting in
         * workspace not found exception and http code 401
         * **/
        try {
            WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            assertThat("GET workspace does not throw not found exception", true);
        } catch (ApiException apiEx) {
            logger.debug("Caught exception getting workspace ", apiEx);
            assertThat("GET workspace throws not found exception", true);
        }

        int httpCode = workspaceApi.getApiClient().getStatusCode();
        logger.info("GET workspace HTTP code: {}", httpCode);
        assertThat(httpCode, equalTo(401));
    }
}
