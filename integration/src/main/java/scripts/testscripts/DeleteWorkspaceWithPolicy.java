package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.TpsApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.TpsPaoGetResult;
import bio.terra.workspace.model.WorkspaceDescription;
import java.util.List;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateWithPolicyTestScriptBase;

public class DeleteWorkspaceWithPolicy extends WorkspaceAllocateWithPolicyTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(DeleteWorkspaceWithPolicy.class);

  private static final String DATASET_RESOURCE_NAME = "wsmtest_dataset";

  @Override
  protected void doUserJourney(TestUserSpecification sourceOwnerUser, WorkspaceApi workspaceApi)
      throws Exception {
    TpsApi tpsClient = ClientTestUtils.getTpsClient(sourceOwnerUser, server);

    WorkspaceDescription workspace = workspaceApi.getWorkspace(getWorkspaceId(), null);
    assertNotNull(workspace.getPolicies());

    TpsPaoGetResult tpsResult = tpsClient.getPao(getWorkspaceId());
    assertNotNull(tpsResult);

    // Delete the workspace, which should delete the policy
    workspaceApi.deleteWorkspace(getWorkspaceId());

    // Confirm the workspace is deleted
    var workspaceMissingException =
        assertThrows(
            ApiException.class,
            () -> workspaceApi.getWorkspace(getWorkspaceId(), /*minimumHighestRole=*/ null));
    assertEquals(HttpStatus.SC_NOT_FOUND, workspaceMissingException.getCode());

    // Confirm the policy is deleted
    var policyMissingException =
        assertThrows(ApiException.class, () -> tpsClient.getPao(getWorkspaceId()));
    assertEquals(HttpStatus.SC_NOT_FOUND, policyMissingException.getCode());
  }

  /**
   * If this test succeeds, it will clean up the workspace as part of the user journey, meaning a
   * "not found" exception should not be considered an error here.
   */
  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    try {
      workspaceApi.deleteWorkspace(getWorkspaceId());
    } catch (ApiException e) {
      if (e.getCode() != HttpStatus.SC_NOT_FOUND) {
        throw e;
      }
    }
  }
}
