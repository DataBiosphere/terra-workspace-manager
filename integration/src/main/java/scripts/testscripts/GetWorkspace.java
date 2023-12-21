package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.WorkspaceDescription;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class GetWorkspace extends WorkspaceAllocateTestScriptBase {
  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    // This GetWorkspace test expects a valid workspace id created by the setup step.
    // Throw exception if anything goes wrong
    final WorkspaceDescription workspaceDescription =
        workspaceApi.getWorkspace(getWorkspaceId(), /* minimumHighestRole= */ null);
    assertThat(workspaceDescription.getId(), equalTo(getWorkspaceId()));

    // userFacingId was not set in CreateWorkspace request. Make sure it is set now.
    assertThat(workspaceDescription.getUserFacingId().length(), greaterThan(0));
  }
}
