package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.BUCKET_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
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
  public void createGetDeleteControlledBigQueryDataset() {
    UUID workspaceId = createGcpWorkspace();
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
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
    final ControlledGcsBucketResource initialResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId1)
            .bucketName(BUCKET_NAME)
            .build();

    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    final ControlledGcsBucketResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId2)
            .name("another-bucket-resource")
            .bucketName(BUCKET_NAME)
            .build();

    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    // clean up
    resourceDao.deleteResource(initialResource.getWorkspaceId(), initialResource.getResourceId());
    resourceDao.deleteResource(
        duplicatingResource.getWorkspaceId(), duplicatingResource.getResourceId());
  }

  // AI Notebooks are unique on the tuple {instanceId, location, projectId } in addition
  // to the underlying requirement that resource ID and resource names are unique within a
  // workspace.
  @Test
  public void duplicateNotebookIsRejected() {
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledResource initialResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .build();
    resourceDao.createControlledResource(initialResource);
    assertEquals(
        initialResource,
        resourceDao.getResource(initialResource.getWorkspaceId(), initialResource.getResourceId()));

    final ControlledResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .name("resource-2")
            .build();
    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    final ControlledResource resourceWithDifferentWorkspaceId =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(createGcpWorkspace())
            .name("resource-3")
            .build();

    // should be fine: separate workspaces implies separate gcp projects
    resourceDao.createControlledResource(resourceWithDifferentWorkspaceId);

    assertEquals(
        resourceWithDifferentWorkspaceId,
        resourceDao.getResource(
            resourceWithDifferentWorkspaceId.getWorkspaceId(),
            resourceWithDifferentWorkspaceId.getResourceId()));

    final ControlledResource resourceWithDifferentLocation =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .name("resource-4")
            .location("somewhere-else")
            .build();

    // same project & instance ID but different location from resource1
    resourceDao.createControlledResource(resourceWithDifferentLocation);
    assertEquals(
        resourceWithDifferentLocation,
        resourceDao.getResource(
            resourceWithDifferentLocation.getWorkspaceId(),
            resourceWithDifferentLocation.getResourceId()));

    // clean up
    resourceDao.deleteResource(initialResource.getWorkspaceId(), initialResource.getResourceId());
    // resource2 never got created
    resourceDao.deleteResource(
        resourceWithDifferentWorkspaceId.getWorkspaceId(),
        resourceWithDifferentWorkspaceId.getResourceId());
    resourceDao.deleteResource(
        resourceWithDifferentLocation.getWorkspaceId(),
        resourceWithDifferentLocation.getResourceId());
  }

  @Test
  public void duplicateBigQueryDatasetRejected() {
    String datasetName1 = "dataset1";
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledBigQueryDatasetResource initialResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspaceId1)
            .datasetName(datasetName1)
            .build();

    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    // This is in a different workspace (and so a different cloud context), so it is not a conflict
    // even with the same Dataset ID.
    final ControlledBigQueryDatasetResource uniqueResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspaceId2)
            .name("uniqueResourceName")
            .datasetName(datasetName1)
            .build();
    resourceDao.createControlledResource(uniqueResource);

    // This is in the same workspace as initialResource, so it should be a conflict.
    final ControlledBigQueryDatasetResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryDatasetResource()
            .workspaceId(workspaceId1)
            .name("differentResourceName")
            .datasetName(datasetName1)
            .build();

    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    // clean up
    resourceDao.deleteResource(initialResource.getWorkspaceId(), initialResource.getResourceId());
    resourceDao.deleteResource(uniqueResource.getWorkspaceId(), uniqueResource.getResourceId());
    resourceDao.deleteResource(
        duplicatingResource.getWorkspaceId(), duplicatingResource.getResourceId());
  }
}
