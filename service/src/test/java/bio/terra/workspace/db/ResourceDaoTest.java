package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_SUBJECT_ID;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.createWorkspaceWithGcpContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
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
public class ResourceDaoTest extends BaseSpringBootUnitTest {
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;
  @Autowired GcpCloudContextService gcpCloudContextService;
  @Autowired WorkspaceActivityLogDao activityLogDao;
  @Autowired RawDaoTestFixture rawDaoTestFixture;

  private UUID workspaceUuid;

  @BeforeAll
  public void setUp() {
    workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
  }

  @AfterAll
  public void cleanUp() {
    WorkspaceUnitTestUtils.deleteCloudContextInDatabase(
        workspaceDao, workspaceUuid, CloudPlatform.GCP);
    WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao);
  }

  @Test
  public void createGetControlledGcsBucket_beforeLogIsWrite_lastUpdatedDateEqualsCreatedDate() {
    ControlledGcsBucketResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertNotNull(getResource.getCreatedDate());
    assertEquals(getResource.getCreatedDate(), getResource.getLastUpdatedDate());
    assertEquals(getResource.getCreatedByEmail(), getResource.getLastUpdatedByEmail());
  }

  @Test
  public void createGetControlledGcsBucket() {
    ControlledGcsBucketResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertEquals(DEFAULT_USER_EMAIL, getResource.getLastUpdatedByEmail());
    assertNotNull(getResource.getLastUpdatedDate());
  }

  @Test
  public void createGetControlledFlexResource() {
    ControlledFlexibleResource resource =
        ControlledResourceFixtures.makeDefaultFlexResourceBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);
    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertNotNull(getResource.getCreatedDate());
    assertNotNull(getResource.getLastUpdatedDate());
  }

  @Test
  public void
      createGetDeleteControlledBigQueryDataset_beforeLogIsWrite_lastUpdatedDateEqualsCreatedDate() {
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertNotNull(getResource.getCreatedDate());
    assertEquals(getResource.getCreatedDate(), getResource.getLastUpdatedDate());
    assertEquals(getResource.getCreatedByEmail(), getResource.getLastUpdatedByEmail());
  }

  @Test
  public void createGetDeleteControlledBigQueryDataset() {
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertEquals(DEFAULT_USER_EMAIL, getResource.getLastUpdatedByEmail());
    assertNotNull(getResource.getLastUpdatedDate());
  }

  @Test
  public void
      createGetControlledAiNotebookInstance_beforeLogIsWrite_lastUpdatedDateEqualsCreatedDate() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertNotNull(getResource.getCreatedDate());
    assertEquals(getResource.getCreatedDate(), getResource.getLastUpdatedDate());
    assertEquals(getResource.getCreatedByEmail(), getResource.getLastUpdatedByEmail());
  }

  @Test
  public void createGetControlledAiNotebookInstance() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);
    activityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog(
            DEFAULT_USER_EMAIL,
            DEFAULT_USER_SUBJECT_ID,
            OperationType.CREATE,
            resource.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE));

    WsmResource getResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertTrue(resource.partialEqual(getResource));
    assertEquals(DEFAULT_USER_EMAIL, getResource.getLastUpdatedByEmail());
    assertNotNull(getResource.getLastUpdatedDate());
  }

  @Test
  public void updateControlledResourceRegion() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);

    String newRegion = "great-new-world";

    assertTrue(resourceDao.updateControlledResourceRegion(resource.getResourceId(), newRegion));
    String newUserEmail = "foo";
    activityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog(
            newUserEmail,
            UUID.randomUUID().toString(),
            OperationType.UPDATE,
            resource.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE));

    ControlledResource controlledResource =
        resourceDao.getResource(workspaceUuid, resource.getResourceId()).castToControlledResource();
    assertEquals(newRegion, controlledResource.getRegion());
    assertEquals(newUserEmail, controlledResource.getLastUpdatedByEmail());
    assertFalse(
        controlledResource.getLastUpdatedDate().isBefore(controlledResource.getCreatedDate()));
  }

  @Test
  public void updateControlledResourceRegion_regionNull() {
    ControlledResourceFields commonFields =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource resource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, resource);

    assertTrue(resourceDao.updateControlledResourceRegion(resource.getResourceId(), null));

    ControlledResource controlledResource =
        resourceDao.getResource(workspaceUuid, resource.getResourceId()).castToControlledResource();
    assertNull(controlledResource.getRegion());
  }

  @Test
  public void listAndDeleteControlledResourceInContext() {
    UUID workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
    ControlledGcsBucketResource bucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid).build();
    ControlledBigQueryDatasetResource dataset =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(bucket);
    createControlledResourceAndLog(dataset);

    List<ControlledResource> gcpList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.GCP);
    List<ControlledResource> azureList =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.AZURE);
    List<ControlledResource> allCloudList =
        resourceDao.listControlledResources(workspaceUuid, null);

    assertTrue(azureList.isEmpty());
    // note that list ordering is important in this test, reverse create date order
    assertEquals(
        gcpList.stream().map(ControlledResource::getResourceId).toList(),
        List.of(dataset.getResourceId(), bucket.getResourceId()));
    assertEquals(
        allCloudList.stream().map(ControlledResource::getResourceId).toList(),
        List.of(dataset.getResourceId(), bucket.getResourceId()));

    assertTrue(resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.GCP));
    assertFalse(resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.AZURE));
    List<ControlledResource> listAfterDeletion =
        resourceDao.listControlledResources(workspaceUuid, CloudPlatform.GCP);
    assertTrue(listAfterDeletion.isEmpty());
    WorkspaceUnitTestUtils.deleteCloudContextInDatabase(
        workspaceDao, workspaceUuid, CloudPlatform.GCP);
  }

  @Test
  public void duplicateResourceCreateDoesNotDelete() {
    String bucketName = "my-real-bucket-name";
    // Run the normal case
    ControlledGcsBucketResource initialBucket =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid)
            .bucketName(bucketName)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, initialBucket);
    // Later calls to createResourceFailure (e.g. from duplicate requests) should not undo
    // resources they did not create.
    resourceDao.createResourceFailure(
        initialBucket,
        UUID.randomUUID().toString(),
        /* exception= */ null,
        WsmResourceStateRule.DELETE_ON_FAILURE);
    ControlledGcsBucketResource retrievedBucket =
        resourceDao
            .getResource(initialBucket.getWorkspaceId(), initialBucket.getResourceId())
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);
    assertTrue(initialBucket.partialEqual(retrievedBucket));
  }

  @Test
  public void duplicateControlledBucketNameRejected() {
    String clashingBucketName = "not-a-pail";
    ControlledGcsBucketResource initialResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid)
            .bucketName(clashingBucketName)
            .build();
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, initialResource);

    UUID workspaceId2 = createWorkspaceWithGcpContext(workspaceDao);
    ControlledGcsBucketResource duplicatingResource =
        ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceId2)
            .bucketName(clashingBucketName)
            .build();

    assertThrows(
        DuplicateResourceException.class,
        () ->
            ControlledResourceFixtures.insertControlledResourceRow(
                resourceDao, duplicatingResource));
  }

  // AI Notebooks are unique on the tuple {instanceId, location, projectId } in addition
  // to the underlying requirement that resource ID and resource names are unique within a
  // workspace.
  @Test
  public void
      createAiNotebook_duplicateCloudInstanceId_rejectedWhenInSameCloudProjectAndLocation() {
    String cloudInstanceId = TestUtils.appendRandomNumber("my-cloud-instance-id");
    ControlledResourceFields commonFields1 =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .build();
    ControlledAiNotebookInstanceResource initialResource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields1)
            .instanceId(cloudInstanceId)
            .build();

    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, initialResource);
    assertTrue(
        initialResource.partialEqual(
            resourceDao.getResource(
                initialResource.getWorkspaceId(), initialResource.getResourceId())));

    ControlledResourceFields commonFields2 =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name("resource-2")
            .build();
    ControlledResource duplicatingResource =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields2)
            .instanceId(cloudInstanceId)
            .build();
    assertThrows(
        DuplicateResourceException.class,
        () ->
            ControlledResourceFixtures.insertControlledResourceRow(
                resourceDao, duplicatingResource));

    ControlledResourceFields commonFields3 =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(createWorkspaceWithGcpContext(workspaceDao))
            .name("resource-3")
            .build();
    ControlledResource resourceWithDifferentWorkspaceId =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields3)
            .instanceId(cloudInstanceId)
            .build();

    // should be fine: separate workspaces implies separate gcp projects
    createControlledResourceAndLog(resourceWithDifferentWorkspaceId);
    assertTrue(
        resourceWithDifferentWorkspaceId.partialEqual(
            resourceDao.getResource(
                resourceWithDifferentWorkspaceId.getWorkspaceId(),
                resourceWithDifferentWorkspaceId.getResourceId())));

    ControlledResourceFields commonFields5 =
        ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
            .workspaceUuid(workspaceUuid)
            .name("resource-5")
            .build();
    ControlledAiNotebookInstanceResource resourceWithDefaultLocation =
        ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
            .common(commonFields5)
            .instanceId(cloudInstanceId)
            .location(null)
            .build();

    assertThrows(
        DuplicateResourceException.class,
        () ->
            resourceDao.createResourceStart(
                resourceWithDefaultLocation, UUID.randomUUID().toString()));
  }

  @Test
  public void duplicateBigQueryDatasetRejected() {
    String datasetName1 = "dataset1";
    String projectId1 = "projectId1";
    String projectId2 = "projectId2";
    ControlledBigQueryDatasetResource initialResource =
        ControlledBigQueryDatasetResource.builder()
            .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
            .projectId(projectId1)
            .datasetName(datasetName1)
            .build();
    createControlledResourceAndLog(initialResource);

    UUID workspaceId2 = createWorkspaceWithGcpContext(workspaceDao);
    try {
      // This is in a different workspace (and so a different cloud context), so it is not a
      // conflict
      // even with the same Dataset ID.
      ControlledBigQueryDatasetResource uniqueResource =
          ControlledBigQueryDatasetResource.builder()
              .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceId2))
              .datasetName(datasetName1)
              .projectId(projectId2)
              .build();
      createControlledResourceAndLog(uniqueResource);

      // This is in the same workspace as initialResource, so it should be a conflict.
      ControlledBigQueryDatasetResource duplicatingResource =
          ControlledBigQueryDatasetResource.builder()
              .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(workspaceUuid))
              .projectId(projectId1)
              .datasetName(datasetName1)
              .build();

      assertThrows(
          DuplicateResourceException.class,
          () -> createControlledResourceAndLog(duplicatingResource));
    } finally {
      resourceDao.deleteAllControlledResources(workspaceId2, CloudPlatform.GCP);
    }
  }

  @Test
  public void updateResourceProperties_propertiesUpdated() {
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);
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
  }

  @Test
  public void updateResourceProperties_lastUpdatedBy() {
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);
    Map<String, String> properties = Map.of("foo", "bar1", "sweet", "cake");
    WsmResource resourceBeforeUpdate =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());

    resourceDao.updateResourceProperties(workspaceUuid, resource.getResourceId(), properties);
    String userEmail = "foo";
    activityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog(
            userEmail,
            UUID.randomUUID().toString(),
            OperationType.UPDATE,
            resource.getResourceId().toString(),
            ActivityLogChangedTarget.CONTROLLED_GCP_BIG_QUERY_DATASET));

    WsmResource resourceAfterUpdate =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    assertEquals(userEmail, resourceAfterUpdate.getLastUpdatedByEmail());
    assertTrue(
        resourceAfterUpdate
            .getLastUpdatedDate()
            .isAfter(resourceBeforeUpdate.getLastUpdatedDate()));
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
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);

    assertThrows(
        MissingRequiredFieldsException.class,
        () ->
            resourceDao.updateResourceProperties(
                workspaceUuid, resource.getResourceId(), Map.of()));
  }

  @Test
  public void updateResourceSuccess_throwDuplicateResourceException() {
    ControlledBigQueryDatasetResource dataset1 =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(dataset1);
    ControlledBigQueryDatasetResource dataset2 =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(dataset2);
    // update dataset2 to dataset1's name.
    CommonUpdateParameters updateParams = new CommonUpdateParameters().setName(dataset1.getName());
    var dbUpdater =
        resourceDao.updateResourceStart(
            workspaceUuid, dataset2.getResourceId(), updateParams, null);

    assertThrows(
        DuplicateResourceException.class,
        () ->
            resourceDao.updateResourceSuccess(
                workspaceUuid, dataset2.getResourceId(), dbUpdater, null));
  }

  @Test
  public void deleteResourceProperties_resourcePropertiesDeleted() {
    ControlledBigQueryDatasetResource resource =
        ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid).build();
    createControlledResourceAndLog(resource);

    resourceDao.deleteResourceProperties(
        workspaceUuid,
        resource.getResourceId(),
        DEFAULT_RESOURCE_PROPERTIES.keySet().stream().toList());
    assertTrue(
        resourceDao.getResource(workspaceUuid, resource.getResourceId()).getProperties().isEmpty());
  }

  @Test
  public void deleteResourceProperties_nonExistingKeys_nothingIsDeleted() {
    UUID workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
    try {
      ControlledBigQueryDatasetResource resource =
          ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid)
              .build();
      createControlledResourceAndLog(resource);

      resourceDao.deleteResourceProperties(
          workspaceUuid, resource.getResourceId(), List.of(RandomStringUtils.randomAlphabetic(3)));

      assertEquals(
          resource.getProperties(),
          resourceDao.getResource(workspaceUuid, resource.getResourceId()).getProperties());
    } finally {
      resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.GCP);
    }
  }

  @Test
  public void deleteResourceProperties_noKeySpecified_throwsMissingRequiredFieldsException() {
    UUID workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
    try {
      ControlledBigQueryDatasetResource resource =
          ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid)
              .build();
      createControlledResourceAndLog(resource);

      assertThrows(
          MissingRequiredFieldsException.class,
          () ->
              resourceDao.deleteResourceProperties(
                  workspaceUuid, resource.getResourceId(), List.of()));
    } finally {
      resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.GCP);
    }
  }

  @Test
  void gcsBucketWithUnderscore_retrieve() {
    UUID workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
    UUID resourceId = UUID.randomUUID();
    String bucketName = "gcs_bucket_with_underscore_name";
    // This is an artificially contrived situation where we create a gcs bucket with an underscore.
    ControlledResource originalResource =
        new ControlledGcsBucketResource(
            new DbResource()
                .workspaceUuid(workspaceUuid)
                .resourceId(resourceId)
                .name(TestUtils.appendRandomNumber("resourcename"))
                .resourceType(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET)
                .stewardshipType(StewardshipType.CONTROLLED)
                .description("This is a bucket with underscore name")
                .cloningInstructions(CloningInstructions.COPY_NOTHING)
                .assignedUser(null)
                .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
                .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                .managedBy(ManagedByType.MANAGED_BY_USER)
                .applicationId(null)
                .resourceLineage(List.of())
                .properties(Map.of())
                .createdByEmail("foo@bar.com")
                .region("us-central1"),
            bucketName);

    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, originalResource);

    ControlledGcsBucketResource bucket =
        resourceDao
            .getResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.CONTROLLED_GCP_GCS_BUCKET);

    assertEquals(bucketName, bucket.getBucketName());
  }

  @Test
  public void listControlledResourceWithoutRegion() {
    UUID workspaceUuid = createWorkspaceWithGcpContext(workspaceDao);
    UUID workspaceUuid2 = createWorkspaceWithGcpContext(workspaceDao);
    UUID workspaceUuid3 = createWorkspaceWithGcpContext(workspaceDao);
    for (int i = 0; i < 5; i++) {
      ControlledBigQueryDatasetResource dataset =
          ControlledGcpResourceFixtures.makeDefaultControlledBqDatasetBuilder(workspaceUuid)
              .common(
                  ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                      .workspaceUuid(workspaceUuid)
                      .region(null)
                      .build())
              .build();
      ControlledGcsBucketResource bucket =
          ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder(workspaceUuid2)
              .common(
                  ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                      .workspaceUuid(workspaceUuid2)
                      .region(null)
                      .build())
              .build();
      ControlledAiNotebookInstanceResource notebook =
          ControlledGcpResourceFixtures.makeDefaultAiNotebookInstanceBuilder()
              .common(
                  ControlledGcpResourceFixtures.makeNotebookCommonFieldsBuilder()
                      .workspaceUuid(workspaceUuid3)
                      .region(null)
                      .build())
              .build();
      ControlledResourceFixtures.insertControlledResourceRow(resourceDao, dataset);
      ControlledResourceFixtures.insertControlledResourceRow(resourceDao, bucket);
      ControlledResourceFixtures.insertControlledResourceRow(resourceDao, notebook);
    }

    assertEquals(
        15, resourceDao.listControlledResourcesWithMissingRegion(CloudPlatform.GCP).size());
    assertTrue(resourceDao.listControlledResourcesWithMissingRegion(CloudPlatform.AZURE).isEmpty());
    resourceDao.deleteAllControlledResources(workspaceUuid, CloudPlatform.GCP);
    resourceDao.deleteAllControlledResources(workspaceUuid2, CloudPlatform.GCP);
    resourceDao.deleteAllControlledResources(workspaceUuid3, CloudPlatform.GCP);
  }

  private void createControlledResourceAndLog(ControlledResource resource) {
    var flightId = UUID.randomUUID().toString();
    resourceDao.createResourceStart(resource, flightId);
    resourceDao.createResourceSuccess(resource, flightId);
    activityLogDao.writeActivity(
        workspaceUuid,
        new DbWorkspaceActivityLog(
            DEFAULT_USER_EMAIL,
            DEFAULT_USER_SUBJECT_ID,
            OperationType.CREATE,
            resource.getResourceId().toString(),
            resource.getResourceType().getActivityLogChangedTarget()));
  }
}
