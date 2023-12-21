package bio.terra.workspace.service.resource.referenced;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.BaseUnitTestMockDataRepoService;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotAttributes;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.terra.workspace.ReferencedTerraWorkspaceResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class ReferencedResourceServiceTest extends BaseUnitTestMockDataRepoService {

  private static final Logger logger = LoggerFactory.getLogger(ReferencedResourceServiceTest.class);
  private static final String DATA_REPO_INSTANCE_NAME = "terra";
  private static final String FAKE_PROJECT_ID = "fakeprojecctid";

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private JobService jobService;
  @Autowired private WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired private WsmResourceService wsmResourceService;

  private UUID workspaceUuid;
  private ReferencedResource referencedResource;

  @BeforeEach
  void setup() throws InterruptedException {
    doReturn(true).when(mockDataRepoService()).snapshotReadable(any(), any(), any());
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    workspaceUuid = createMcTestWorkspace();
  }

  @AfterEach
  void teardown() {
    jobService.setFlightDebugInfoForTest(null);
    if (referencedResource != null) {
      try {
        referenceResourceService.deleteReferenceResourceForResourceType(
            referencedResource.getWorkspaceId(),
            referencedResource.getResourceId(),
            referencedResource.getResourceType(),
            USER_REQUEST);
      } catch (Exception ex) {
        logger.warn("Failed to delete reference resource " + referencedResource.getResourceId());
      }
    }
    WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao);
  }

  @Test
  void updateDataRepoReferenceTarget_updateSnapshotIdOnly() {
    referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
    referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

    UUID resourceId = referencedResource.getResourceId();
    ReferencedDataRepoSnapshotResource originalResource =
        referenceResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    String originalName = referencedResource.getName();
    String originalDescription = referencedResource.getDescription();
    String originalInstanceName = originalResource.getInstanceName();

    var updateDetailsBeforeResourceUpdate =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
    assertTrue(updateDetailsBeforeResourceUpdate.isPresent());

    String newSnapshotId = "new_snapshot_id";
    var attributes = new ReferencedDataRepoSnapshotAttributes(null, newSnapshotId);
    wsmResourceService.updateResource(
        USER_REQUEST, referencedResource, new CommonUpdateParameters(), attributes);

    ReferencedDataRepoSnapshotResource result =
        referenceResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    var lastUpdateDetailsAfterResourceUpdate =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        updateDetailsBeforeResourceUpdate
            .get()
            .changeDate()
            .isBefore(lastUpdateDetailsAfterResourceUpdate.get().changeDate()));
    assertEquals(
        new ActivityLogChangeDetails(
            workspaceUuid,
            lastUpdateDetailsAfterResourceUpdate.get().changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.UPDATE,
            resourceId.toString(),
            ActivityLogChangedTarget.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
        lastUpdateDetailsAfterResourceUpdate.get());
    assertEquals(originalName, result.getName());
    assertEquals(originalDescription, result.getDescription());
    assertEquals(originalInstanceName, result.getInstanceName());
    assertEquals(newSnapshotId, result.getSnapshotId());
  }

  @Test
  void updateDataRepoReferenceTarget_updateSnapshotIdAndInstanceName() {
    referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
    referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

    UUID resourceId = referencedResource.getResourceId();
    String originalName = referencedResource.getName();
    String originalDescription = referencedResource.getDescription();

    String newSnapshotId = "new_snapshot_id";
    String newInstanceName = "new_instance_name";
    var attributes = new ReferencedDataRepoSnapshotAttributes(newInstanceName, newSnapshotId);
    wsmResourceService.updateResource(
        USER_REQUEST, referencedResource, new CommonUpdateParameters(), attributes);

    ReferencedDataRepoSnapshotResource result =
        referenceResourceService
            .getReferenceResource(workspaceUuid, resourceId)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    assertEquals(originalName, result.getName());
    assertEquals(originalDescription, result.getDescription());
    assertEquals(newInstanceName, result.getInstanceName());
    assertEquals(newSnapshotId, result.getSnapshotId());
  }

  @Test
  void updateNameDescriptionAndCloningInstructions() {
    referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
    referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

    // Change the name & cloning instructions
    String updatedName = "renamed-" + referencedResource.getName();
    String originalDescription = referencedResource.getDescription();
    CloningInstructions updatedCloningInstructions = CloningInstructions.COPY_REFERENCE;
    var commonUpdateParameters =
        new CommonUpdateParameters()
            .setDescription(null)
            .setName(updatedName)
            .setCloningInstructions(StewardshipType.REFERENCED, updatedCloningInstructions);
    wsmResourceService.updateResource(
        USER_REQUEST, referencedResource, commonUpdateParameters, /* updateParameters= */ null);

    referencedResource =
        referenceResourceService.getReferenceResourceByName(workspaceUuid, updatedName);
    assertEquals(referencedResource.getName(), updatedName);
    assertEquals(referencedResource.getDescription(), originalDescription);
    assertEquals(updatedCloningInstructions, referencedResource.getCloningInstructions());

    // Change the description
    String updatedDescription = "updated " + referencedResource.getDescription();
    CloningInstructions noUpdateCloningInstructions = null;

    var lastUpdateDetailsBeforeResourceUpdate =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
    commonUpdateParameters
        .setDescription(updatedDescription)
        .setName(null)
        .setCloningInstructions(StewardshipType.REFERENCED, noUpdateCloningInstructions);
    wsmResourceService.updateResource(
        USER_REQUEST, referencedResource, commonUpdateParameters, /* updateParameters= */ null);

    referencedResource =
        referenceResourceService.getReferenceResource(
            workspaceUuid, referencedResource.getResourceId());
    var lastUpdateDetailsAfterResourceUpdate =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
    assertTrue(
        lastUpdateDetailsBeforeResourceUpdate
            .get()
            .changeDate()
            .isBefore(lastUpdateDetailsAfterResourceUpdate.get().changeDate()));
    assertEquals(
        new ActivityLogChangeDetails(
            workspaceUuid,
            lastUpdateDetailsAfterResourceUpdate.get().changeDate(),
            USER_REQUEST.getEmail(),
            USER_REQUEST.getSubjectId(),
            OperationType.UPDATE,
            referencedResource.getResourceId().toString(),
            ActivityLogChangedTarget.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
        lastUpdateDetailsAfterResourceUpdate.get());
    assertEquals(updatedName, referencedResource.getName());
    assertEquals(updatedDescription, referencedResource.getDescription());

    // Change both
    String updatedName2 = "2" + updatedName;
    String updatedDescription2 = "2" + updatedDescription;
    commonUpdateParameters.setDescription(updatedDescription2).setName(updatedName2);
    wsmResourceService.updateResource(
        USER_REQUEST, referencedResource, commonUpdateParameters, /* updateParameters= */ null);

    referencedResource =
        referenceResourceService.getReferenceResource(
            workspaceUuid, referencedResource.getResourceId());
    assertEquals(referencedResource.getName(), updatedName2);
    assertEquals(referencedResource.getDescription(), updatedDescription2);

    // Update to invalid name is rejected.
    String invalidName = "!!!!invalid_name!!!";
    assertThrows(
        InvalidNameException.class,
        () -> commonUpdateParameters.setDescription(null).setName(invalidName));
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * MC_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createMcTestWorkspace() {
    Workspace request = WorkspaceFixtures.buildMcWorkspace();
    return workspaceService.createWorkspace(request, null, null, null, USER_REQUEST);
  }

  @Nested
  class WsmValidityChecks {
    // Test that all of the WsmResource validity checks catch invalid input

    @Test
    void testInvalidWorkspaceId() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(null).build())
                  .instanceName(DATA_REPO_INSTANCE_NAME)
                  .snapshotId("polaroid")
                  .build());
    }

    @Test
    void testInvalidName() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                          .name(null)
                          .build())
                  .instanceName(DATA_REPO_INSTANCE_NAME)
                  .snapshotId("polaroid")
                  .build());
    }

    @Test
    void testInvalidCloningInstructions() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                          .cloningInstructions(null)
                          .build())
                  .instanceName(DATA_REPO_INSTANCE_NAME)
                  .snapshotId("polaroid")
                  .build());
    }

    @Test
    void testInvalidResourceId() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                          .resourceId(null)
                          .build())
                  .instanceName(DATA_REPO_INSTANCE_NAME)
                  .snapshotId("polaroid")
                  .build());
    }
  }

  // case - get not found by id and name
  // case - delete not found
  // case - update cases

  @Nested
  class DataRepoReference {
    // Test that all of the WsmResource validity checks catch invalid input

    @Test
    void testDataRepoReference() {
      referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedDataRepoSnapshotResource resource =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      var lastUpdateDetailsBeforeResourceCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsBeforeResourceCreate.isPresent());
      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

      var lastUpdateDetailsAfterCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsBeforeResourceCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterCreate.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterCreate.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.CREATE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
          lastUpdateDetailsAfterCreate.get());

      ReferencedDataRepoSnapshotResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      assertTrue(resource.partialEqual(resultResource));

      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));

      ReferencedDataRepoSnapshotResource byid =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      ReferencedDataRepoSnapshotResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, resource.getName())
              .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      assertTrue(byid.partialEqual(byname));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT,
          USER_REQUEST);
      var lastUpdateDetailsAfterDelete =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterDelete.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterDelete.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.DELETE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_ANY_DATA_REPO_SNAPSHOT),
          lastUpdateDetailsAfterDelete.get());
    }

    @Test
    void testInvalidInstanceName() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .instanceName(null)
                  .snapshotId("polaroid")
                  .build());
    }

    @Test
    void testInvalidSnapshot() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedDataRepoSnapshotResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .instanceName(DATA_REPO_INSTANCE_NAME)
                  .snapshotId(null)
                  .build());
    }

    @Test
    void testInvalidCast() {
      referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
      assertThrows(
          BadRequestException.class,
          () -> referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET));

      WsmResource wsmResource = referencedResource;
      assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
    }
  }

  @Nested
  class GcpBucketReference {

    @BeforeEach
    void setup() {
      // Make the Verify step always succeed
      doReturn(true).when(mockCrlService()).canReadGcsBucket(any(), any());
      doReturn(true).when(mockCrlService()).canReadGcsObject(any(), any(), any());
    }

    private ReferencedGcsObjectResource makeGcsObjectReference() {
      return ReferencedGcsObjectResource.builder()
          .wsmResourceFields(ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
          .bucketName("theres-a-hole-in-the-bottom-of-the")
          .objectName("balloon")
          .build();
    }

    @Test
    void gcsObjectReference() {
      referencedResource = makeGcsObjectReference();
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedGcsObjectResource resource =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);

      var lastUpdateDetailsBeforeResourceCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsBeforeResourceCreate.isPresent());
      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

      var lastUpdateDetailsAfterCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsBeforeResourceCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterCreate.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterCreate.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.CREATE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_GCS_OBJECT),
          lastUpdateDetailsAfterCreate.get());

      ReferencedGcsObjectResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      assertTrue(resource.partialEqual(resultResource));

      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));

      ReferencedGcsObjectResource byid =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      ReferencedGcsObjectResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, resource.getName())
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      assertNotNull(byid);
      assertTrue(byid.partialEqual(byname));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_GCS_OBJECT,
          USER_REQUEST);
      var lastUpdateDetailsAfterDelete =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterDelete.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterDelete.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.DELETE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_GCS_OBJECT),
          lastUpdateDetailsAfterDelete.get());
    }

    private ReferencedGcsBucketResource makeGcsBucketResource() {
      return ReferencedGcsBucketResource.builder()
          .wsmResourceFields(ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
          .bucketName("theres-a-hole-in-the-bottom-of-the")
          .build();
    }

    @Test
    void testGcsBucketReference() {
      referencedResource = makeGcsBucketResource();
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedGcsBucketResource resource =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);

      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);
      ReferencedGcsBucketResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      assertTrue(resource.partialEqual(resultResource));

      // Mock Sam will not return real credentials for a pet SA to make this call, but we don't
      // need real credentials because we also mock out cloud validation here.
      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));

      ReferencedGcsBucketResource byid =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      ReferencedGcsBucketResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, resource.getName())
              .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      assertTrue(byid.partialEqual(byname));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_GCS_BUCKET,
          USER_REQUEST);
    }

    @Test
    void missingObjectName_throwsException() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedGcsObjectResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .bucketName("spongebob")
                  .objectName(null)
                  .build());
    }

    @Test
    void testMissingBucketName() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedGcsBucketResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .bucketName(null)
                  .build());
    }

    @Test
    void testInvalidBucketName() {
      assertThrows(
          InvalidNameException.class,
          () ->
              ReferencedGcsBucketResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .bucketName("Buckets don't accept * in the names, either")
                  .build());
    }

    @Test
    void testInvalidCast() {
      referencedResource = makeGcsBucketResource();
      assertThrows(
          BadRequestException.class,
          () -> referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET));

      WsmResource wsmResource = referencedResource;
      assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
    }
  }

  @Nested
  class BigQueryReference {

    private static final String DATASET_NAME = "testbq_datasetname";
    private static final String DATA_TABLE_NAME = "testbq datatablename";

    @BeforeEach
    void setup() {
      // Make the Verify step always succeed
      doReturn(true).when(mockCrlService()).canReadBigQueryDataset(any(), any(), any());
      doReturn(true).when(mockCrlService()).canReadBigQueryDataTable(any(), any(), any(), any());
    }

    private ReferencedBigQueryDatasetResource makeBigQueryDatasetResource() {
      UUID resourceId = UUID.randomUUID();
      String resourceName = "testbq-" + resourceId;

      return ReferencedBigQueryDatasetResource.builder()
          .wsmResourceFields(
              ReferenceResourceFixtures.makeDefaultWsmResourceFieldBuilder(workspaceUuid)
                  .resourceId(resourceId)
                  .name(resourceName)
                  .build())
          .datasetName(DATASET_NAME)
          .projectId(FAKE_PROJECT_ID)
          .build();
    }

    private ReferencedBigQueryDataTableResource makeBigQueryDataTableResource() {
      return ReferencedBigQueryDataTableResource.builder()
          .wsmResourceFields(ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
          .datasetId(DATASET_NAME)
          .dataTableId(DATA_TABLE_NAME)
          .projectId(FAKE_PROJECT_ID)
          .build();
    }

    @Test
    void testBigQueryDatasetReference() {
      referencedResource = makeBigQueryDatasetResource();
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedBigQueryDatasetResource resource =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      assertEquals(resource.getResourceType(), WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);

      var lastUpdateDetailsBeforeCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsBeforeCreate.isPresent());
      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);
      assertThrows(
          DuplicateResourceException.class,
          () ->
              referenceResourceService.createReferenceResource(
                  resultReferenceResource, USER_REQUEST));

      var lastUpdateDetailsAfterCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsBeforeCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterCreate.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterCreate.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.CREATE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_BIG_QUERY_DATASET),
          lastUpdateDetailsAfterCreate.get());

      ReferencedBigQueryDatasetResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      assertTrue(resource.partialEqual(resultResource));

      // Mock Sam will not return real credentials for a pet SA to make this call, but we don't
      // need real credentials because we also mock out cloud validation here.
      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));

      ReferencedBigQueryDatasetResource byid =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      ReferencedBigQueryDatasetResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, resource.getName())
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      assertTrue(byid.partialEqual(byname));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET,
          USER_REQUEST);
      var lastUpdateDetailsAfterDelete =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterDelete.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterDelete.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.DELETE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_BIG_QUERY_DATASET),
          lastUpdateDetailsAfterDelete.get());
    }

    @Test
    void bigQueryDataTableReference() {
      referencedResource = makeBigQueryDataTableResource();
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedBigQueryDataTableResource resource =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      assertEquals(resource.getDataTableId(), DATA_TABLE_NAME);
      assertEquals(resource.getDatasetId(), DATASET_NAME);

      var lastUpdateDetailsBeforeCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsBeforeCreate.isPresent());
      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

      var lastUpdateDetailsAfterCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsBeforeCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterCreate.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterCreate.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.CREATE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
          lastUpdateDetailsAfterCreate.get());

      ReferencedBigQueryDataTableResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      assertTrue(resource.partialEqual(resultResource));
      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));
      ReferencedBigQueryDataTableResource byid =
          referenceResourceService
              .getReferenceResource(workspaceUuid, resource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      ReferencedBigQueryDataTableResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, resource.getName())
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      assertTrue(byid.partialEqual(byname));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE,
          USER_REQUEST);

      var lastUpdateDetailsAfterDelete =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterDelete.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterDelete.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.DELETE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
          lastUpdateDetailsAfterDelete.get());
    }

    @Test
    void bigQueryDataTableReference_deleteWithWrongTypeThenRightType_doesNotDeleteFirstTime() {
      referencedResource = makeBigQueryDataTableResource();

      referenceResourceService
          .createReferenceResource(referencedResource, USER_REQUEST)
          .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET,
          USER_REQUEST);

      var lastUpdateDetailsBeforeCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      // Fail to delete the resource the first time with the wrong resource type.
      ReferencedBigQueryDataTableResource resource =
          referenceResourceService
              .getReferenceResource(workspaceUuid, referencedResource.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      assertTrue(
          resource.partialEqual(
              referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE)));
      var lastUpdateDetailsAfterFailedDeletion =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsAfterFailedDeletion.isPresent());
      assertEquals(lastUpdateDetailsBeforeCreate.get(), lastUpdateDetailsAfterFailedDeletion.get());
      assertEquals(
          lastUpdateDetailsBeforeCreate.get().changeDate(),
          lastUpdateDetailsAfterFailedDeletion.get().changeDate());

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE,
          USER_REQUEST);
      // BQ data table is successfully deleted.
      assertThrows(
          ResourceNotFoundException.class,
          () ->
              referenceResourceService.getReferenceResource(
                  workspaceUuid, referencedResource.getResourceId()));

      var lastUpdateDetailsAfterSuccessfulDeletion =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterFailedDeletion
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterSuccessfulDeletion.get().changeDate()));
    }

    @Test
    void createReferencedBigQueryDatasetResource_missesProjectId_throwsException() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedBigQueryDatasetResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .projectId(null)
                  .datasetName("testbq_datasetname")
                  .build());
    }

    @Test
    void createReferencedBigQueryDataTableResource_missesDataTableName_throwsException() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedBigQueryDataTableResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .projectId("testbq-projectid")
                  .datasetId("testbq_datasetname")
                  .dataTableId(null)
                  .build());
    }

    @Test
    void createReferencedBigQueryDatasetResource_missesDatasetName_throwsException() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              ReferencedBigQueryDataTableResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .projectId("testbq-projectid")
                  .datasetId("")
                  .build());
    }

    @Test
    void createReferencedBigQueryDataTableResource_invalidDataTableName_throwsException() {
      assertThrows(
          InvalidNameException.class,
          () ->
              ReferencedBigQueryDataTableResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .projectId("testbq-projectid")
                  .datasetId("testbq_datasetname")
                  .dataTableId("*&%@#")
                  .build());
    }

    @Test
    void createReferencedBigQueryDatasetResource_invalidDatasetName_throwsException() {
      assertThrows(
          InvalidReferenceException.class,
          () ->
              ReferencedBigQueryDatasetResource.builder()
                  .wsmResourceFields(
                      ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
                  .projectId("testbq-projectid")
                  .datasetName("Nor do datasets; neither ' nor *")
                  .build());
    }

    @Test
    void testInvalidCast() {
      referencedResource = makeBigQueryDatasetResource();
      assertThrows(
          BadRequestException.class,
          () -> referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET));
      WsmResource wsmResource = referencedResource;
      assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
    }
  }

  @Nested
  class TerraWorkspaceReference {
    final UUID referencedWorkspaceId = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
      doReturn(true).when(mockSamService()).isAuthorized(any(), any(), any(), any());
    }

    private ReferencedTerraWorkspaceResource makeTerraWorkspaceReference() {
      return ReferencedTerraWorkspaceResource.builder()
          .wsmResourceFields(ReferenceResourceFixtures.makeDefaultWsmResourceFields(workspaceUuid))
          .referencedWorkspaceId(referencedWorkspaceId)
          .build();
    }

    @Test
    void terraWorkspaceReference() {
      referencedResource = makeTerraWorkspaceReference();
      assertEquals(StewardshipType.REFERENCED, referencedResource.getStewardshipType());

      ReferencedTerraWorkspaceResource expected =
          referencedResource.castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);

      var lastUpdateDetailsBeforeCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(lastUpdateDetailsBeforeCreate.isPresent());
      ReferencedResource actualReferencedResourceGeneric =
          referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);
      var lastUpdateDetailsAfterCreate =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsBeforeCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterCreate.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterCreate.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.CREATE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_ANY_TERRA_WORKSPACE),
          lastUpdateDetailsAfterCreate.get());
      ReferencedTerraWorkspaceResource actual =
          actualReferencedResourceGeneric.castByEnum(
              WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
      assertTrue(expected.partialEqual(actual));

      assertTrue(
          referenceResourceService.checkAccess(
              workspaceUuid, referencedResource.getResourceId(), USER_REQUEST));

      ReferencedTerraWorkspaceResource byIdActual =
          referenceResourceService
              .getReferenceResource(workspaceUuid, expected.getResourceId())
              .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
      ReferencedTerraWorkspaceResource byNameActual =
          referenceResourceService
              .getReferenceResourceByName(workspaceUuid, expected.getName())
              .castByEnum(WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE);
      assertNotNull(byIdActual);
      assertTrue(byIdActual.partialEqual(byNameActual));

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceUuid,
          referencedResource.getResourceId(),
          WsmResourceType.REFERENCED_ANY_TERRA_WORKSPACE,
          USER_REQUEST);
      var lastUpdateDetailsAfterDelete =
          workspaceActivityLogService.getLastUpdatedDetails(workspaceUuid);
      assertTrue(
          lastUpdateDetailsAfterCreate
              .get()
              .changeDate()
              .isBefore(lastUpdateDetailsAfterDelete.get().changeDate()));
      assertEquals(
          new ActivityLogChangeDetails(
              workspaceUuid,
              lastUpdateDetailsAfterDelete.get().changeDate(),
              USER_REQUEST.getEmail(),
              USER_REQUEST.getSubjectId(),
              OperationType.DELETE,
              referencedResource.getResourceId().toString(),
              ActivityLogChangedTarget.REFERENCED_ANY_TERRA_WORKSPACE),
          lastUpdateDetailsAfterDelete.get());
    }

    @Test
    void testInvalidCast() {
      referencedResource = makeTerraWorkspaceReference();
      assertThrows(
          BadRequestException.class,
          () -> referencedResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET));

      WsmResource wsmResource = referencedResource;
      assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
    }
  }

  @Nested
  class NegativeTests {

    @Test
    void getResourceById() {
      assertThrows(
          ResourceNotFoundException.class,
          () -> referenceResourceService.getReferenceResource(workspaceUuid, UUID.randomUUID()));
    }

    @Test
    void getResourceByName() {
      assertThrows(
          ResourceNotFoundException.class,
          () ->
              referenceResourceService.getReferenceResourceByName(
                  workspaceUuid, UUID.randomUUID().toString()));
    }

    @Test
    void testDuplicateResourceName() {
      referencedResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceUuid);
      assertEquals(referencedResource.getStewardshipType(), StewardshipType.REFERENCED);

      referenceResourceService.createReferenceResource(referencedResource, USER_REQUEST);

      UUID resourceId = UUID.randomUUID();
      ReferencedDataRepoSnapshotResource duplicateNameResource =
          new ReferencedDataRepoSnapshotResource(
              WsmResourceFields.builder()
                  .workspaceUuid(workspaceUuid)
                  .resourceId(resourceId)
                  .name(referencedResource.getName())
                  .cloningInstructions(CloningInstructions.COPY_NOTHING)
                  .createdByEmail(DEFAULT_USER_EMAIL)
                  .build(),
              DATA_REPO_INSTANCE_NAME,
              "polaroid");

      assertThrows(
          DuplicateResourceException.class,
          () ->
              referenceResourceService.createReferenceResource(
                  duplicateNameResource, USER_REQUEST));
    }
  }
}
