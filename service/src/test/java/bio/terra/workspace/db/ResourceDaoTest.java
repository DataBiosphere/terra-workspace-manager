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
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
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
    WorkspaceFixtures.createGcpCloudContext(
        workspaceDao, workspace.getWorkspaceId(), "my-project-id");
    return workspace.getWorkspaceId();
  }

  @Test
  public void createGetControlledGcsBucket() {
    UUID workspaceId = createGcpWorkspace();
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId).build();
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
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(workspaceId)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields).build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));

    resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void listAndDeleteControlledResourceInContext() {
    UUID workspaceId = createGcpWorkspace();
    ControlledGcsBucketResource bucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId).build();
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
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId1)
            .bucketName(clashingBucketName)
            .build();

    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    final ControlledGcsBucketResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId2)
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
    ControlledResourceFields commonFields1 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(workspaceId1)
            .build();
    ControlledAiNotebookInstanceResource initialResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields1).build();
    resourceDao.createControlledResource(initialResource);
    assertEquals(
        initialResource,
        resourceDao.getResource(initialResource.getWorkspaceId(), initialResource.getResourceId()));

    ControlledResourceFields commonFields2 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(workspaceId1)
            .name("resource-2")
            .build();
    final ControlledResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields2).build();
    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    ControlledResourceFields commonFields3 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(createGcpWorkspace())
            .name("resource-3")
            .build();
    final ControlledResource resourceWithDifferentWorkspaceId =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields3).build();

    // should be fine: separate workspaces implies separate gcp projects
    resourceDao.createControlledResource(resourceWithDifferentWorkspaceId);

    assertEquals(
        resourceWithDifferentWorkspaceId,
        resourceDao.getResource(
            resourceWithDifferentWorkspaceId.getWorkspaceId(),
            resourceWithDifferentWorkspaceId.getResourceId()));

    ControlledResourceFields commonFields4 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(workspaceId1)
            .name("resource-4")
            .build();
    final ControlledResource resourceWithDifferentLocation =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .common(commonFields4)
            .location("somewhere-else")
            .build();

    // same project & instance ID but different location from resource1
    resourceDao.createControlledResource(resourceWithDifferentLocation);
    assertEquals(
        resourceWithDifferentLocation,
        resourceDao.getResource(
            resourceWithDifferentLocation.getWorkspaceId(),
            resourceWithDifferentLocation.getResourceId()));

    ControlledResourceFields commonFields5 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceId(workspaceId1)
            .name("resource-5")
            .build();
    final ControlledAiNotebookInstanceResource resourceWithDefaultLocation =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .common(commonFields5)
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
    String projectId1 = "projectId1";
    String projectId2 = "projectId2";
    final UUID workspaceId1 = createGcpWorkspace();
    final ControlledBigQueryDatasetResource initialResource =
        ControlledBigQueryDatasetResource.builder()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId1))
            .projectId(projectId1)
            .datasetName(datasetName1)
            .build();
    resourceDao.createControlledResource(initialResource);

    final UUID workspaceId2 = createGcpWorkspace();
    // This is in a different workspace (and so a different cloud context), so it is not a conflict
    // even with the same Dataset ID.
    final ControlledBigQueryDatasetResource uniqueResource =
        ControlledBigQueryDatasetResource.builder()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId2))
            .datasetName(datasetName1)
            .projectId(projectId2)
            .build();
    resourceDao.createControlledResource(uniqueResource);

    // This is in the same workspace as initialResource, so it should be a conflict.
    final ControlledBigQueryDatasetResource duplicatingResource =
        ControlledBigQueryDatasetResource.builder()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId1))
            .projectId(projectId1)
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
