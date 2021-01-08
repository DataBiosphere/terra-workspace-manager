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
    private UUID workspaceId;
    private CreatedWorkspace workspace;

    @Override
    public void setup(List<TestUserSpecification> testUsers) throws Exception {
        assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
        workspaceId = UUID.randomUUID();
        WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUsers.get(0), server);
        try {
            CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
            requestBody.setId(workspaceId);
            workspace = workspaceApi.createWorkspace(requestBody);
        } catch (ApiException apiEx) {
            logger.debug("Caught exception creating workspace ", apiEx);
        }
        WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE workspace");
    }

    @Override
    public void userJourney(TestUserSpecification testUser) throws Exception {
        WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUser, server);

        /**
         * This GetWorkspace test expects a valid workspace id
         * created by the setup step.
         *
         * Throw exception if anything goes wrong
         * **/
        WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspace.getId());
        WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "GET workspace");
    }

    @Override
    public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
        assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
        WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUsers.get(0), server);
        try {
            workspaceApi.deleteWorkspace(workspace.getId());
        } catch (ApiException apiEx) {
            logger.debug("Caught exception deleting workspace ", apiEx);
        }
        WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "DELETE workspace");
    }
}
