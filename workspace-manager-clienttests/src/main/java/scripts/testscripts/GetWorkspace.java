package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.WorkspaceDescription;
import scripts.utils.WorkspaceAllocateTestScriptBase;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GetWorkspace extends WorkspaceAllocateTestScriptBase {
    @Override
    public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
        throws ApiException {
        /**
         * This GetWorkspace test expects a valid workspace id
         * created by the setup step.
         *
         * Throw exception if anything goes wrong
         * **/
        final WorkspaceDescription workspaceDescription = workspaceApi
            .getWorkspace(getWorkspaceId());
        assertThat(workspaceDescription.getId(), equalTo(getWorkspaceId()));
    }
}
