package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateTerraWorkspaceReferenceRequestBody;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import bio.terra.workspace.model.TerraWorkspaceAttributes;
import bio.terra.workspace.model.TerraWorkspaceResource;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.GcpWorkspaceCloneTestScriptBase;
import scripts.utils.RetryUtils;
import scripts.utils.TestUtils;

// Subclass GcpWorkspaceCloneTestScriptBase because that creates 2 workspaces. In this test, we
// create a referenced Terra workspace resource that points to the "destination workspace".
public class ReferencedTerraWorkspaceLifecycle extends GcpWorkspaceCloneTestScriptBase {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedTerraWorkspaceLifecycle.class);

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ReferencedGcpResourceApi referencedGcpResourceApi =
        ClientTestUtils.getReferencedGcpResourceClient(testUser, server);

    UUID createdResourceId = testCreateAndGet(referencedGcpResourceApi);
    testAttemptToCreateReferenceToNonExistingWorkspace(referencedGcpResourceApi);
    testDelete(referencedGcpResourceApi, createdResourceId);
  }

  private UUID getReferencedWorkspaceId() {
    return getDestinationWorkspaceId();
  }

  /* Returns created resource ID. */
  private UUID testCreateAndGet(ReferencedGcpResourceApi referencedGcpResourceApi)
      throws Exception {
    String resourceName = TestUtils.appendRandomNumber("resource-name");

    // Create resource
    var body =
        new CreateTerraWorkspaceReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .name(resourceName))
            .referencedWorkspace(
                new TerraWorkspaceAttributes().referencedWorkspaceId(getReferencedWorkspaceId()));
    TerraWorkspaceResource createdResource =
        RetryUtils.getWithRetryOnException(
            () -> referencedGcpResourceApi.createTerraWorkspaceReference(body, getWorkspaceId()));
    logger.info(
        "In workspace {}, created reference to workspace {}",
        getWorkspaceId(),
        getReferencedWorkspaceId());

    // Get resource by resource id
    TerraWorkspaceResource gotResource =
        RetryUtils.getWithRetryOnException(
            () ->
                referencedGcpResourceApi.getTerraWorkspaceReference(
                    getWorkspaceId(), createdResource.getMetadata().getResourceId()));
    assertEquals(createdResource, gotResource);

    // Get resource by resource name
    gotResource =
        RetryUtils.getWithRetryOnException(
            () ->
                referencedGcpResourceApi.getTerraWorkspaceReferenceByName(
                    getWorkspaceId(), createdResource.getMetadata().getName()));
    assertEquals(createdResource, gotResource);

    return createdResource.getMetadata().getResourceId();
  }

  private void testAttemptToCreateReferenceToNonExistingWorkspace(
      ReferencedGcpResourceApi referencedGcpResourceApi) {
    String resourceName = TestUtils.appendRandomNumber("terra-workspace-reference");

    // Create resource
    var body =
        new CreateTerraWorkspaceReferenceRequestBody()
            .metadata(
                new ReferenceResourceCommonFields()
                    .cloningInstructions(CloningInstructionsEnum.NOTHING)
                    .name(resourceName))
            .referencedWorkspace(
                new TerraWorkspaceAttributes().referencedWorkspaceId(UUID.randomUUID()));
    var workspaceNotExist =
        assertThrows(
            ApiException.class,
            () -> referencedGcpResourceApi.createTerraWorkspaceReference(body, getWorkspaceId()));
    assertEquals(HttpStatus.SC_NOT_FOUND, workspaceNotExist.getCode());
  }

  private void testDelete(ReferencedGcpResourceApi referencedGcpResourceApi, UUID createdResourceId)
      throws Exception {
    // Delete resource
    RetryUtils.runWithRetryOnException(
        () -> {
          try {
            referencedGcpResourceApi.deleteTerraWorkspaceReference(
                getWorkspaceId(), createdResourceId);
          } catch (ApiException e) {
            fail("Exception when calling deleteTerraWorkspaceReference: " + e.getMessage());
          }
        });
    logger.info(
        "In workspace {}, deleted reference to workspace {}",
        getWorkspaceId(),
        getReferencedWorkspaceId());

    // Make sure resource no longer exists by attempting to get it
    try {
      referencedGcpResourceApi.getTerraWorkspaceReference(getWorkspaceId(), createdResourceId);
    } catch (Exception e) {
      logger.info(e.getMessage());
    }
  }
}
