package bio.terra.workspace.db;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant.DEFAULT_ZONE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ResourceDaoTest extends BaseUnitTest {
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired GcpCloudContextService gcpCloudContextService;

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
    gcpCloudContextService.createGcpCloudContext(
        workspace.getWorkspaceId(),
        new GcpCloudContext("my-project-id"),
        "flight-creategcpworkspace");
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
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceId).build();
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
  public void listAndDeleteControlledResourceInContext() {
    UUID workspaceId = createGcpWorkspace();
    ControlledGcsBucketResource bucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId)
            .build();
    ControlledBigQueryDatasetResource dataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceId).build();
    resourceDao.createControlledResource(bucket);
    resourceDao.createControlledResource(dataset);

    List<ControlledResource> gcpList =
        resourceDao.listControlledResources(workspaceId, CloudPlatform.GCP);
    List<ControlledResource> azureList =
        resourceDao.listControlledResources(workspaceId, CloudPlatform.AZURE);
    List<ControlledResource> allCloudList = resourceDao.listControlledResources(workspaceId, null);

    assertTrue(azureList.isEmpty());
    assertThat(gcpList, containsInAnyOrder(bucket, dataset));
    assertThat(allCloudList, containsInAnyOrder(bucket, dataset));

    assertTrue(resourceDao.deleteAllControlledResources(workspaceId, CloudPlatform.GCP));
    assertFalse(resourceDao.deleteAllControlledResources(workspaceId, CloudPlatform.AZURE));
    List<ControlledResource> listAfterDeletion =
        resourceDao.listControlledResources(workspaceId, CloudPlatform.GCP);
    assertTrue(listAfterDeletion.isEmpty());
  }

  @Test
  public void duplicateControlledBucketNameRejected() {
    final String clashingBucketName = "not-a-pail";
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledGcsBucketResource initialResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId1)
            .bucketName(clashingBucketName)
            .build();

    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    final ControlledGcsBucketResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketResource()
            .workspaceId(workspaceId2)
            .name("another-bucket-resource")
            .bucketName(clashingBucketName)
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

    final ControlledAiNotebookInstanceResource resourceWithDefaultLocation =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspaceId1)
            .name("resource-5")
            .location(null)
            .build();
    resourceDao.createControlledResource(resourceWithDefaultLocation);
    assertEquals(
        resourceWithDefaultLocation,
        resourceDao.getResource(
            resourceWithDefaultLocation.getWorkspaceId(),
            resourceWithDefaultLocation.getResourceId()));

    assertEquals(DEFAULT_ZONE, resourceWithDefaultLocation.getLocation());

    // clean up
    resourceDao.deleteResource(initialResource.getWorkspaceId(), initialResource.getResourceId());
    // resource2 never got created
    resourceDao.deleteResource(
        resourceWithDifferentWorkspaceId.getWorkspaceId(),
        resourceWithDifferentWorkspaceId.getResourceId());
    resourceDao.deleteResource(
        resourceWithDifferentLocation.getWorkspaceId(),
        resourceWithDifferentLocation.getResourceId());
    resourceDao.deleteResource(
        resourceWithDefaultLocation.getWorkspaceId(), resourceWithDefaultLocation.getResourceId());
  }

  @Test
  public void duplicateBigQueryDatasetRejected() {
    String datasetName1 = "dataset1";
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledBigQueryDatasetResource initialResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceId1)
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId1))
            .datasetName(datasetName1)
            .build();
    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    // This is in a different workspace (and so a different cloud context), so it is not a conflict
    // even with the same Dataset ID.
    final ControlledBigQueryDatasetResource uniqueResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceId1)
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId2)
                    .name("uniqueResourcename"))
            .datasetName(datasetName1)
            .build();
    resourceDao.createControlledResource(uniqueResource);

    // This is in the same workspace as initialResource, so it should be a conflict.
    final ControlledBigQueryDatasetResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceId1)
            .common(
                ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId1)
                    .name("differentResourceName"))
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
