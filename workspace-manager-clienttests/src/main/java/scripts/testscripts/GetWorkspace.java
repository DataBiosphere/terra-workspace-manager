package scripts.testscripts;

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
import scripts.utils.WorkspaceTestScriptBase;

public class GetWorkspace extends WorkspaceTestScriptBase {
    private static final Logger logger = LoggerFactory.getLogger(GetWorkspace.class);
    private CreatedWorkspace workspace;

    @Override
    public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
        throws ApiException {
        final UUID workspaceId = UUID.randomUUID();
        final CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
        requestBody.setId(workspaceId);
        workspace = workspaceApi.createWorkspace(requestBody);
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
        final WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspace.getId());
    }

    @Override
    public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
        throws ApiException {
        workspaceApi.deleteWorkspace(workspace.getId());
    }

}
