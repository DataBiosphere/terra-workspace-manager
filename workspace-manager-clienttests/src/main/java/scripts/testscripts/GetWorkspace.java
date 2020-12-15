package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WorkspaceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.WorkspaceManagerServiceUtils;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GetWorkspace extends TestScript {
    private static final Logger logger = LoggerFactory.getLogger(GetWorkspace.class);
    private UUID id;
    private CreatedWorkspace workspace;

    /** Public constructor so that this class can be instantiated via reflection. */
    public GetWorkspace() {
        super();
    }

    public void setup(List<TestUserSpecification> testUsers) throws Exception {
        assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
        id = UUID.randomUUID();
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUsers.get(0), server);
        WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
        try {
            CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
            requestBody.setId(id);
            requestBody.setJobId("testrunner");
            requestBody.setSpendProfile("testrunner");
            workspace = workspaceApi.createWorkspace(requestBody);
        } catch (ApiException apiEx) {
            logger.debug("Caught exception creating workspace ", apiEx);
        }

        int httpCode = workspaceApi.getApiClient().getStatusCode();
        logger.info("CREATE workspace HTTP code: {}", httpCode);
        assertThat(httpCode, equalTo(200));
    }

    public void userJourney(TestUserSpecification testUser) throws Exception {
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUser, server);
        WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);

        /**
         * This GetWorkspace test expects a valid workspace id
         * created from the setup step.
         *
         * Throw workspace not found exception if anything goes wrong
         * **/
        try {
            // String invalidWorkspaceId = "11111111-1111-1111-1111-111111111111";
            WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspace.getId());
            assertThat("GET workspace does not throw not found exception", true);
        } catch (ApiException apiEx) {
            logger.debug("Caught exception getting workspace ", apiEx);
            assertThat("GET workspace throws not found exception", true);
        }

        int httpCode = workspaceApi.getApiClient().getStatusCode();
        logger.info("GET workspace HTTP code: {}", httpCode);
        assertThat(httpCode, equalTo(200));
    }

    public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
        assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
        ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUsers.get(0), server);
        WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);

        try {
            workspaceApi.deleteWorkspace(workspace.getId());
        } catch (ApiException apiEx) {
            logger.debug("Caught exception deleting workspace ", apiEx);
        }

        int httpCode = workspaceApi.getApiClient().getStatusCode();
        logger.info("DELELE workspace HTTP code: {}", httpCode);
        assertThat(httpCode, equalTo(200));
    }
}
