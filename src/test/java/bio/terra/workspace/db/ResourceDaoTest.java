package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BUCKET_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ResourceDaoTest extends BaseUnitTest {
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
   *
   * <p>The {@link ResourceDao#createControlledResource(ControlledResource)} checks that a relevant
   * cloud context exists before storing the resource.
   */
  private UUID createGcpWorkspace() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    workspaceDao.createGcpCloudContext(
        workspace.getWorkspaceId(), new GcpCloudContext("my-project-id"));
    return workspace.getWorkspaceId();
  }

  @Test
  public void createGetControlledGcsBucket() {
    UUID workspaceId = createGcpWorkspace();
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId)
            .build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));
    resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void createGetControlledAiNotebookInstance() {
    UUID workspaceId = createGcpWorkspace();
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().workspaceId(workspaceId).build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));

    resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void duplicateControlledBucketNameRejected() {
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledGcsBucketResource resource1 =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId1)
            .bucketName(BUCKET_NAME)
            .build();

    resourceDao.createControlledResource(resource1);

    final UUID workspaceId2 = createGcpWorkspace();
    final ControlledGcsBucketResource resource2 =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId2)
            .name("another-bucket-resource")
            .bucketName(BUCKET_NAME)
            .build();

    assertThrows(
        DuplicateResourceException.class, () -> resourceDao.createControlledResource(resource2));

    // clean up
    resourceDao.deleteResource(resource1.getWorkspaceId(), resource1.getResourceId());
    resourceDao.deleteResource(resource2.getWorkspaceId(), resource2.getResourceId());
  }

  // AI Notebooks are unique on the tuple {instanceId, location, projectId } in addition
  // to the underlying requirement that resource ID and resource names are unique within a workspace.
  @Test
  public void duplicateNotebookIsRejected() {
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledResource resource1 =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .build();
    resourceDao.createControlledResource(resource1);
    assertEquals(
        resource1, resourceDao.getResource(resource1.getWorkspaceId(), resource1.getResourceId()));

    final ControlledResource resource2 =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .name("resource-2")
            .build();
    assertThrows(
        DuplicateResourceException.class, () -> resourceDao.createControlledResource(resource2));

    final ControlledResource resource3 =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(createGcpWorkspace())
            .name("resource-3")
            .build();

    // should be fine: separate workspaces implies separate gcp projects
    resourceDao.createControlledResource(resource3);

    assertEquals(
        resource3, resourceDao.getResource(resource3.getWorkspaceId(), resource3.getResourceId()));

    final ControlledResource resource4 =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .name("resource-4")
            .location("somewhere-else")
            .build();

    // same project & instance ID but different location from resource1
    resourceDao.createControlledResource(resource4);
    assertEquals(
        resource4, resourceDao.getResource(resource4.getWorkspaceId(), resource4.getResourceId()));

    // clean up
    resourceDao.deleteResource(resource1.getWorkspaceId(), resource1.getResourceId());
    resourceDao.deleteResource(resource2.getWorkspaceId(), resource2.getResourceId());
    resourceDao.deleteResource(resource3.getWorkspaceId(), resource3.getResourceId());
    resourceDao.deleteResource(resource4.getWorkspaceId(), resource4.getResourceId());
  }
}
