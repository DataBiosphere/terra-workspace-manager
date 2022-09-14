package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES;
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
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(Lifecycle.PER_CLASS)
public class ResourceDaoTest extends BaseUnitTest {
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired GcpCloudContextService gcpCloudContextService;

  private UUID workspaceUuid;

  @BeforeAll
  public void setUp() {
    workspaceUuid = createGcpWorkspace();
  }

  @AfterAll
  public void cleanUp() {
    workspaceDao.deleteCloudContext(workspaceUuid, CloudPlatform.GCP);
    workspaceDao.deleteWorkspace(workspaceUuid);
  }

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
   *
   * <p>The {@link ResourceDao#createControlledResource(ControlledResource)} checks that a relevant
   * cloud context exists before storing the resource.
   */
  private UUID createGcpWorkspace() {
    UUID uuid = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId(uuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);
    WorkspaceFixtures.createGcpCloudContextInDatabase(
        workspaceDao, workspace.getWorkspaceId(), "my-project-id");
    return workspace.getWorkspaceId();
  }

  @Test
  public void createGetControlledGcsBucket() {
    ControlledGcsBucketResource resource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));
    // resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void createGetDeleteControlledBigQueryDataset() {
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));
    // resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void createGetControlledAiNotebookInstance() {
    ControlledResourceFields commonFields =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields).build();
    resourceDao.createControlledResource(resource);

    assertEquals(
        resource, resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId()));

    // resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void listAndDeleteControlledResourceInContext() {
    ControlledGcsBucketResource bucket =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledBigQueryDatasetResource dataset =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(bucket);
    resourceDao.createControlledResource(dataset);

    List<ControlledResource> gcpList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.GCP);
    List<ControlledResource> azureList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AZURE);
    List<ControlledResource> allCloudList =
        resourceDao.listControlledResources(workspaceUuid, null);

    assertTrue(azureList.isEmpty());
    assertThat(gcpList, containsInAnyOrder(bucket, dataset));
    assertThat(allCloudList, containsInAnyOrder(bucket, dataset));

    assertTrue(resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.GCP));
    assertFalse(resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.AZURE));
    List<ControlledResource> listAfterDeletion =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.GCP);
    assertTrue(listAfterDeletion.isEmpty());
  }

  @Test
  public void duplicateControlledBucketNameRejected() {
    final String clashingBucketName = "not-a-pail";
    final ControlledGcsBucketResource initialResource =
        ControlledResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid)
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
    // resourceDao.deleteResource(initialResource.getWorkspaceId(),
    // initialResource.getResourceId());
    // resourceDao.deleteResource(
    // duplicatingResource.getWorkspaceId(), duplicatingResource.getResourceId());
  }

  // AI Notebooks are unique on the tuple {instanceId, location, projectId } in addition
  // to the underlying requirement that resource ID and resource names are unique within a
  // workspace.
  @Test
  public void duplicateNotebookIsRejected() {
    ControlledResourceFields commonFields1 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource initialResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields1).build();
    resourceDao.createControlledResource(initialResource);
    assertEquals(
        initialResource,
        resourceDao.getResource(initialResource.getWorkspaceId(), initialResource.getResourceId()));

    ControlledResourceFields commonFields2 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name("resource-2")
            .build();
    final ControlledResource duplicatingResource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance().common(commonFields2).build();
    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    ControlledResourceFields commonFields3 =
        ControlledResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(createGcpWorkspace())
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
            .workspaceUuid(workspaceUuid)
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
            .workspaceUuid(workspaceUuid)
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

    // // clean up
    // resourceDao.deleteResource(initialResource.getWorkspaceId(),
    // initialResource.getResourceId());
    // // resource2 never got created
    // resourceDao.deleteResource(
    //     resourceWithDifferentWorkspaceId.getWorkspaceId(),
    //     resourceWithDifferentWorkspaceId.getResourceId());
    // resourceDao.deleteResource(
    //     resourceWithDifferentLocation.getWorkspaceId(),
    //     resourceWithDifferentLocation.getResourceId());
    // resourceDao.deleteResource(
    //     resourceWithDefaultLocation.getWorkspaceId(),
    // resourceWithDefaultLocation.getResourceId());
  }

  @Test
  public void duplicateBigQueryDatasetRejected() {
    String datasetName1 = "dataset1";
    String projectId1 = "projectId1";
    String projectId2 = "projectId2";
    final ControlledBigQueryDatasetResource initialResource =
        ControlledBigQueryDatasetResource.builder()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
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
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
            .projectId(projectId1)
            .datasetName(datasetName1)
            .build();

    assertThrows(
        DuplicateResourceException.class,
        () -> resourceDao.createControlledResource(duplicatingResource));

    // clean up
    // resourceDao.deleteResource(initialResource.getWorkspaceId(),
    // initialResource.getResourceId());
    // resourceDao.deleteResource(uniqueResource.getWorkspaceId(), uniqueResource.getResourceId());
    // resourceDao.deleteResource(
    //     duplicatingResource.getWorkspaceId(), duplicatingResource.getResourceId());
  }

  @Test
  public void updateResourceProperties_propertiesUpdated() {
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);
    Map<String, String> properties = Map.of("foo", "bar1", "sweet", "cake");

    Map<String, String> expectedUpdatedProperties = new HashMap<>();
    expectedUpdatedProperties.putAll(DEFAULT_RESOURCE_PROPERTIES);
    expectedUpdatedProperties.putAll(properties);
    resourceDao.updateResourceProperties(workspaceUuid, resource.getResourceId(), properties);

    assertEquals(
        expectedUpdatedProperties,
        resourceDao
            .getResource(resource.getWorkspaceId(), resource.getResourceId())
            .getProperties());
    // clean up
    // resourceDao.deleteResource(resource.getWorkspaceId(), resource.getResourceId());
  }

  @Test
  public void updateResourceProperties_resourceNotFound_throwsWorkspaceNotFoundException() {
    Map<String, String> properties = Map.of("foo", "bar1", "sweet", "cake");

    assertThrows(
        ResourceNotFoundException.class,
        () -> resourceDao.updateResourceProperties(workspaceUuid, UUID.randomUUID(), properties));
  }

  @Test
  public void
      updateResourceProperties_emptyUpdateProperties_throwsMissingRequiredFieldsException() {
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertThrows(
        MissingRequiredFieldsException.class,
        () ->
            resourceDao.updateResourceProperties(
                workspaceUuid, resource.getResourceId(), Map.of()));
    // resourceDao.deleteResource(workspaceUuid, resource.getResourceId());
  }

  @Test
  public void updateResourceProperties_nothingIsUpdated_returnsFalse() {
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertFalse(
        resourceDao.updateResourceProperties(
            workspaceUuid, resource.getResourceId(), DEFAULT_RESOURCE_PROPERTIES));
    // resourceDao.deleteResource(workspaceUuid, resource.getResourceId());
  }

  @Test
  public void deleteResourceProperties_resourcePropertiesDeleted() {
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertTrue(
        resourceDao.deleteResourceProperties(
            workspaceUuid,
            resource.getResourceId(),
            DEFAULT_RESOURCE_PROPERTIES.keySet().stream().toList()));
    assertTrue(
        resourceDao.getResource(workspaceUuid, resource.getResourceId()).getProperties().isEmpty());
    // clean up
    // resourceDao.deleteResource(workspaceUuid, resource.getResourceId());
  }

  @Test
  public void deleteResourceProperties_nonExistingKeys_nothingIsDeleted() {
    UUID workspaceUuid = createGcpWorkspace();
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    resourceDao.deleteResourceProperties(
        workspaceUuid, resource.getResourceId(), List.of(RandomStringUtils.randomAlphabetic(3)));

    assertEquals(
        resource.getProperties(),
        resourceDao.getResource(workspaceUuid, resource.getResourceId()).getProperties());
    // clean up
    // resourceDao.deleteResource(workspaceUuid, resource.getResourceId());
  }

  @Test
  public void deleteResourceProperties_noKeySpecified_throwsMissingRequiredFieldsException() {
    UUID workspaceUuid = createGcpWorkspace();
    ControlledBigQueryDatasetResource resource =
        ControlledResourceFixtures.makeDefaultControlledBigQueryBuilder(workspaceUuid).build();
    resourceDao.createControlledResource(resource);

    assertThrows(
        MissingRequiredFieldsException.class,
        () ->
            resourceDao.deleteResourceProperties(
                workspaceUuid, resource.getResourceId(), List.of()));
    // resourceDao.deleteResource(workspaceUuid, resource.getResourceId());
  }
}
