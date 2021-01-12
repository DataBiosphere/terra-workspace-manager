package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WorkspaceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;
import scripts.utils.WorkspaceManagerServiceUtils;
import scripts.utils.WorkspaceTestScriptBase;

public class GetWorkspace extends WorkspaceTestScriptBase {
    private static final Logger logger = LoggerFactory.getLogger(GetWorkspace.class);

    private UUID workspaceId;

    @Override
    public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
        throws ApiException {
        workspaceId = UUID.randomUUID();
        final var requestBody = new CreateWorkspaceRequestBody()
            .id(workspaceId);
        final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
        assertThat(workspace.getId(), equalTo(workspaceId));
        WorkspaceManagerServiceUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");
    }

    @Override
    public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
        throws ApiException {
        /**
         * This GetWorkspace test expects a valid workspace id
         * created by the setup step.
         *
         * Throw exception if anything goes wrong
         * **/
        final WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspaceId);
        assertThat(workspaceDescription.getId(), equalTo(workspaceId));
        WorkspaceManagerServiceUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    }

    @Override
    public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
        throws ApiException {
        workspaceApi.deleteWorkspace(workspaceId);
        WorkspaceManagerServiceUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
    }

}
