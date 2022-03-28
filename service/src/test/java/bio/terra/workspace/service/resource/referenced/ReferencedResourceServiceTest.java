package bio.terra.workspace.service.resource.referenced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.InvalidNameException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable.ReferencedBigQueryDataTableResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsobject.ReferencedGcsObjectResource;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@Tag("unit")
class ReferencedResourceServiceTest extends BaseUnitTest {

  private static final Logger logger = LoggerFactory.getLogger(ReferencedResourceServiceTest.class);
  private static final String DATA_REPO_INSTANCE_NAME = "terra";
  private static final String FAKE_PROJECT_ID = "fakeprojecctid";

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ReferencedResourceService referenceResourceService;
  @Autowired private JobService jobService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  @MockBean private DataRepoService mockDataRepoService;
  @MockBean private CrlService mockCrlService;

  private UUID workspaceId;
  private ReferencedResource referenceResource;

  @BeforeEach
  void setup() {
    doReturn(true).when(mockDataRepoService).snapshotReadable(any(), any(), any());
    workspaceId = createMcTestWorkspace();
    referenceResource = null;
  }

  @AfterEach
  void teardown() throws InterruptedException {
    jobService.setFlightDebugInfoForTest(null);
    if (referenceResource != null) {
      try {
        referenceResourceService.deleteReferenceResourceForResourceType(
            referenceResource.getWorkspaceId(),
            referenceResource.getResourceId(),
            USER_REQUEST,
            referenceResource.getResourceType());
      } catch (Exception ex) {
        logger.warn("Failed to delete reference resource " + referenceResource.getResourceId());
      }
    }
    workspaceDao.deleteWorkspace(workspaceId);
  }

  @Test
  void updateDataRepoReferenceTarget_updateSnapshotIdOnly() {
    referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
    referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

    UUID resourceId = referenceResource.getResourceId();
    ReferencedDataRepoSnapshotResource originalResource =
        referenceResourceService
            .getReferenceResource(workspaceId, resourceId, USER_REQUEST)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    String originalName = referenceResource.getName();
    String originalDescription = referenceResource.getDescription();
    String originalInstanceName = originalResource.getInstanceName();

    String newSnapshotId = "new_snapshot_id";
    ReferencedResource updatedResource =
        originalResource.toBuilder().snapshotId(newSnapshotId).build();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), null, null, updatedResource, USER_REQUEST);

    ReferencedDataRepoSnapshotResource result =
        referenceResourceService
            .getReferenceResource(workspaceId, resourceId, USER_REQUEST)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    assertEquals(originalName, result.getName());
    assertEquals(originalDescription, result.getDescription());
    assertEquals(originalInstanceName, result.getInstanceName());
    assertEquals(newSnapshotId, result.getSnapshotId());
  }

  @Test
  void updateDataRepoReferenceTarget_updateSnapshotIdAndInstanceName() {
    referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
    referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

    UUID resourceId = referenceResource.getResourceId();
    ReferencedDataRepoSnapshotResource originalResource =
        referenceResourceService
            .getReferenceResource(workspaceId, resourceId, USER_REQUEST)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    String originalName = referenceResource.getName();
    String originalDescription = referenceResource.getDescription();

    String newSnapshotId = "new_snapshot_id";
    String newInstanceName = "new_instance_name";
    ReferencedResource updatedResource =
        originalResource.toBuilder()
            .snapshotId(newSnapshotId)
            .instanceName(newInstanceName)
            .build();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), null, null, updatedResource, USER_REQUEST);

    ReferencedDataRepoSnapshotResource result =
        referenceResourceService
            .getReferenceResource(workspaceId, resourceId, USER_REQUEST)
            .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    assertEquals(originalName, result.getName());
    assertEquals(originalDescription, result.getDescription());
    assertEquals(newInstanceName, result.getInstanceName());
    assertEquals(newSnapshotId, result.getSnapshotId());
  }

  @Test
  void updateNameAndDescription() {
    referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
    referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

    // Change the name
    String updatedName = "renamed" + referenceResource.getName();
    String originalDescription = referenceResource.getDescription();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), updatedName, null, USER_REQUEST);
    referenceResource =
        referenceResourceService.getReferenceResourceByName(workspaceId, updatedName, USER_REQUEST);
    assertEquals(referenceResource.getName(), updatedName);
    assertEquals(referenceResource.getDescription(), originalDescription);

    // Change the description
    String updatedDescription = "updated" + referenceResource.getDescription();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), null, updatedDescription, USER_REQUEST);
    referenceResource =
        referenceResourceService.getReferenceResource(
            workspaceId, referenceResource.getResourceId(), USER_REQUEST);
    assertEquals(referenceResource.getName(), updatedName);
    assertEquals(referenceResource.getDescription(), updatedDescription);

    // Change both
    String updatedName2 = "2" + updatedName;
    String updatedDescription2 = "2" + updatedDescription;
    referenceResourceService.updateReferenceResource(
        workspaceId,
        referenceResource.getResourceId(),
        updatedName2,
        updatedDescription2,
        USER_REQUEST);
    referenceResource =
        referenceResourceService.getReferenceResource(
            workspaceId, referenceResource.getResourceId(), USER_REQUEST);
    assertEquals(referenceResource.getName(), updatedName2);
    assertEquals(referenceResource.getDescription(), updatedDescription2);

    // Update to invalid name is rejected.
    String invalidName = "!!!!invalid_name!!!";
    assertThrows(
        InvalidNameException.class,
        () ->
            referenceResourceService.updateReferenceResource(
                workspaceId, referenceResource.getResourceId(), invalidName, null, USER_REQUEST));
    // Update to invalid description
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * MC_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createMcTestWorkspace() {
    Workspace request =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .spendProfileId(null)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, USER_REQUEST);
  }

  @Nested
  class FlightChecks {

    // Test idempotency of stairway steps
    @Test
    void createReferencedResourceDo() {
      Map<String, StepStatus> retrySteps = new HashMap<>();
      retrySteps.put(
          CreateReferenceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
      FlightDebugInfo debugInfo = FlightDebugInfo.newBuilder().doStepFailures(retrySteps).build();
      jobService.setFlightDebugInfoForTest(debugInfo);
      referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
      ReferencedResource createdResource =
          referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
      assertEquals(referenceResource, createdResource);
    }

    @Test
    @SuppressFBWarnings(
        value = "DLS_DEAD_LOCAL_STORE",
        justification =
            "referencedDataRepoSnapshotResource field is unused because the test is to"
                + "test undo step.")
    void createReferencedResourceUndo() {
      Map<String, StepStatus> retrySteps = new HashMap<>();
      retrySteps.put(
          CreateReferenceMetadataStep.class.getName(), StepStatus.STEP_RESULT_FAILURE_RETRY);
      FlightDebugInfo debugInfo =
          FlightDebugInfo.newBuilder().undoStepFailures(retrySteps).lastStepFailure(true).build();
      jobService.setFlightDebugInfoForTest(debugInfo);
      UUID resourceId = UUID.randomUUID();
      referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
      ReferencedDataRepoSnapshotResource unused =
          new ReferencedDataRepoSnapshotResource(
              workspaceId,
              resourceId,
              "aname",
              "some description",
              CloningInstructions.COPY_NOTHING,
              DATA_REPO_INSTANCE_NAME,
              "polaroid");
      // Service methods which wait for a flight to complete will throw an
      // InvalidResultStateException when that flight fails without a cause, which occurs when a
      // flight fails via debugInfo.
      assertThrows(
          InvalidResultStateException.class,
          () -> referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST));
      // The flight should be undone, so the resource should not exist.
      assertThrows(
          ResourceNotFoundException.class,
          () ->
              referenceResourceService.getReferenceResource(workspaceId, resourceId, USER_REQUEST));
    }
  }

  @Nested
  class WsmValidityChecks {
    // Test that all of the WsmResource validity checks catch invalid input

    @Test
    void testInvalidWorkspaceId() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  null,
                  UUID.randomUUID(),
                  "aname",
                  null,
                  CloningInstructions.COPY_NOTHING,
                  DATA_REPO_INSTANCE_NAME,
                  "polaroid"));
    }

    @Test
    void testInvalidName() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  workspaceId,
                  UUID.randomUUID(),
                  null,
                  null,
                  CloningInstructions.COPY_NOTHING,
                  DATA_REPO_INSTANCE_NAME,
                  "polaroid"));
    }

    @Test
    void testInvalidCloningInstructions() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  workspaceId,
                  UUID.randomUUID(),
                  "aname",
                  null,
                  null,
                  DATA_REPO_INSTANCE_NAME,
                  "polaroid"));
    }

    @Test
    void testInvalidResourceId() {
      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  workspaceId, null, "aname", null, null, DATA_REPO_INSTANCE_NAME, "polaroid"));
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
      referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
      assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

      ReferencedDataRepoSnapshotResource resource =
          referenceResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);

      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
      ReferencedDataRepoSnapshotResource resultResource =
          resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      assertEquals(resource, resultResource);

      assertTrue(
          referenceResourceService.checkAccess(
              workspaceId, referenceResource.getResourceId(), USER_REQUEST));

      ReferencedDataRepoSnapshotResource byid =
          referenceResourceService
              .getReferenceResource(workspaceId, resource.getResourceId(), USER_REQUEST)
              .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      ReferencedDataRepoSnapshotResource byname =
          referenceResourceService
              .getReferenceResourceByName(workspaceId, resource.getName(), USER_REQUEST)
              .castByEnum(WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
      assertEquals(byid, byname);

      referenceResourceService.deleteReferenceResourceForResourceType(
          workspaceId,
          referenceResource.getResourceId(),
          USER_REQUEST,
          WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT);
    }

    @Test
    void testInvalidInstanceName() {
      UUID resourceId = UUID.randomUUID();
      String resourceName = "testdatarepo-" + resourceId.toString();

      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  workspaceId,
                  resourceId,
                  resourceName,
                  "description of " + resourceName,
                  CloningInstructions.COPY_NOTHING,
                  null,
                  "polaroid"));
    }

    @Test
    void testInvalidSnapshot() {
      UUID resourceId = UUID.randomUUID();
      String resourceName = "testdatarepo-" + resourceId.toString();

      assertThrows(
          MissingRequiredFieldException.class,
          () ->
              new ReferencedDataRepoSnapshotResource(
                  workspaceId,
                  resourceId,
                  resourceName,
                  "description of " + resourceName,
                  CloningInstructions.COPY_NOTHING,
                  DATA_REPO_INSTANCE_NAME,
                  null));
    }

    @Test
    void testInvalidCast() {
      referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
      assertThrows(
          BadRequestException.class,
          () -> referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET));

      WsmResource wsmResource = referenceResource;
      assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
    }

    @Test
    void testEnumerate() {
      List<ReferencedResource> resources = new ArrayList<>();

      for (int i = 0; i < 3; i++) {
        logger.info("testEnumerate - create resource {}", i);
        ReferencedResource resource =
            ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
        referenceResourceService.createReferenceResource(resource, USER_REQUEST);
        resources.add(resource);
      }

      try {
        logger.info("testEnumerate - enumeration");
        List<ReferencedResource> daoResources =
            referenceResourceService.enumerateReferences(workspaceId, 0, 100, USER_REQUEST);
        logger.info("testEnumerate - got {}", daoResources.size());
        assertEquals(daoResources.size(), resources.size());
      } finally {
        for (var resource : resources) {
          referenceResourceService.deleteReferenceResourceForResourceType(
              workspaceId, resource.getResourceId(), USER_REQUEST, resource.getResourceType());
        }
      }
    }

    @Nested
    class GcpBucketReference {

      @BeforeEach
      void setup() throws Exception {
        // Make the Verify step always succeed
        doReturn(true).when(mockCrlService).canReadGcsBucket(any(), any());
        doReturn(true).when(mockCrlService).canReadGcsObject(any(), any(), any());
      }

      private ReferencedGcsObjectResource makeGcsObjectReference() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testgcs-" + resourceId.toString();

        return new ReferencedGcsObjectResource(
            workspaceId,
            resourceId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_DEFINITION,
            /*bucketName=*/ "theres-a-hole-in-the-bottom-of-the",
            /*objectName=*/ "balloon");
      }

      @Test
      void gcsObjectReference() {
        referenceResource = makeGcsObjectReference();
        assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

        ReferencedGcsObjectResource resource =
            referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
        ReferencedGcsObjectResource resultResource =
            resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
        assertEquals(resource, resultResource);

        assertTrue(
            referenceResourceService.checkAccess(
                workspaceId, referenceResource.getResourceId(), USER_REQUEST));

        ReferencedGcsObjectResource byid =
            referenceResourceService
                .getReferenceResource(workspaceId, resource.getResourceId(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
        ReferencedGcsObjectResource byname =
            referenceResourceService
                .getReferenceResourceByName(workspaceId, resource.getName(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
        assertNotNull(byid);
        assertEquals(byid, byname);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_GCS_OBJECT);
      }

      private ReferencedGcsBucketResource makeGcsBucketResource() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testgcs-" + resourceId.toString();

        return new ReferencedGcsBucketResource(
            workspaceId,
            resourceId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_DEFINITION,
            "theres-a-hole-in-the-bottom-of-the");
      }

      @Test
      void testGcsBucketReference() {
        referenceResource = makeGcsBucketResource();
        assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

        ReferencedGcsBucketResource resource =
            referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
        ReferencedGcsBucketResource resultResource =
            resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
        assertEquals(resource, resultResource);

        // Mock Sam will not return real credentials for a pet SA to make this call, but we don't
        // need real credentials because we also mock out cloud validation here.
        assertTrue(
            referenceResourceService.checkAccess(
                workspaceId, referenceResource.getResourceId(), USER_REQUEST));

        ReferencedGcsBucketResource byid =
            referenceResourceService
                .getReferenceResource(workspaceId, resource.getResourceId(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
        ReferencedGcsBucketResource byname =
            referenceResourceService
                .getReferenceResourceByName(workspaceId, resource.getName(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
        assertEquals(byid, byname);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_GCS_BUCKET);
      }

      @Test
      void missingObjectName_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testgcs-" + resourceId.toString();
        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                new ReferencedGcsObjectResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_DEFINITION,
                    /*bucketName=*/ "spongebob",
                    /*fileName=*/ ""));
      }

      @Test
      void testMissingBucketName() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testgcs-" + resourceId.toString();
        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                new ReferencedGcsBucketResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_DEFINITION,
                    null));
      }

      @Test
      void testInvalidBucketName() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testgcs-" + resourceId.toString();

        assertThrows(
            InvalidNameException.class,
            () ->
                new ReferencedGcsBucketResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_DEFINITION,
                    "Buckets don't accept * in the names, either"));
      }

      @Test
      void testInvalidCast() {
        referenceResource = makeGcsBucketResource();
        assertThrows(
            BadRequestException.class,
            () -> referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET));

        WsmResource wsmResource = referenceResource;
        assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
      }
    }

    @Nested
    class BigQueryReference {

      private static final String DATASET_NAME = "testbq_datasetname";
      private static final String DATA_TABLE_NAME = "testbq datatablename";

      @BeforeEach
      void setup() throws Exception {
        // Make the Verify step always succeed
        doReturn(true).when(mockCrlService).canReadBigQueryDataset(any(), any(), any());
        doReturn(true).when(mockCrlService).canReadBigQueryDataTable(any(), any(), any(), any());
      }

      private ReferencedBigQueryDatasetResource makeBigQueryDatasetResource() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testbq-" + resourceId.toString();

        return new ReferencedBigQueryDatasetResource(
            workspaceId,
            resourceId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_REFERENCE,
            FAKE_PROJECT_ID,
            DATASET_NAME);
      }

      private ReferencedBigQueryDataTableResource makeBigQueryDataTableResource() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testbq-" + resourceId;
        return new ReferencedBigQueryDataTableResource(
            workspaceId,
            resourceId,
            resourceName,
            "description of " + resourceName,
            CloningInstructions.COPY_REFERENCE,
            FAKE_PROJECT_ID,
            DATASET_NAME,
            DATA_TABLE_NAME);
      }

      @Test
      void testBigQueryDatasetReference() {
        referenceResource = makeBigQueryDatasetResource();
        assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

        ReferencedBigQueryDatasetResource resource =
            referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
        assertEquals(resource.getResourceType(), WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
        ReferencedBigQueryDatasetResource resultResource =
            resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
        assertEquals(resource, resultResource);

        // Mock Sam will not return real credentials for a pet SA to make this call, but we don't
        // need real credentials because we also mock out cloud validation here.
        assertTrue(
            referenceResourceService.checkAccess(
                workspaceId, referenceResource.getResourceId(), USER_REQUEST));

        ReferencedBigQueryDatasetResource byid =
            referenceResourceService
                .getReferenceResource(workspaceId, resource.getResourceId(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
        ReferencedBigQueryDatasetResource byname =
            referenceResourceService
                .getReferenceResourceByName(workspaceId, resource.getName(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
        assertEquals(byid, byname);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);
      }

      @Test
      void bigQueryDataTableReference() {
        referenceResource = makeBigQueryDataTableResource();
        assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

        ReferencedBigQueryDataTableResource resource =
            referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        assertEquals(resource.getDataTableId(), DATA_TABLE_NAME);
        assertEquals(resource.getDatasetId(), DATASET_NAME);

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

        ReferencedBigQueryDataTableResource resultResource =
            resultReferenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        assertEquals(resource, resultResource);
        assertTrue(
            referenceResourceService.checkAccess(
                workspaceId, referenceResource.getResourceId(), USER_REQUEST));
        ReferencedBigQueryDataTableResource byid =
            referenceResourceService
                .getReferenceResource(workspaceId, resource.getResourceId(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        ReferencedBigQueryDataTableResource byname =
            referenceResourceService
                .getReferenceResourceByName(workspaceId, resource.getName(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        assertEquals(byid, byname);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
      }

      @Test
      void bigQueryDataTableReference_deleteWithWrongTypeThenRightType_doesNotDeleteFirstTime() {
        referenceResource = makeBigQueryDataTableResource();

        referenceResourceService
            .createReferenceResource(referenceResource, USER_REQUEST)
            .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET);

        // Fail to delete the resource the first time with the wrong resource type.
        ReferencedBigQueryDataTableResource resource =
            referenceResourceService
                .getReferenceResource(workspaceId, referenceResource.getResourceId(), USER_REQUEST)
                .castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        assertEquals(
            referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE),
            resource);

        referenceResourceService.deleteReferenceResourceForResourceType(
            workspaceId,
            referenceResource.getResourceId(),
            USER_REQUEST,
            WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE);
        // BQ data table is successfully deleted.
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                referenceResourceService.getReferenceResource(
                    workspaceId, referenceResource.getResourceId(), USER_REQUEST));
      }

      @Test
      void createReferencedBigQueryDatasetResource_missesProjectId_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                new ReferencedBigQueryDatasetResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_NOTHING,
                    null,
                    "testbq_datasetname"));
      }

      @Test
      void createReferencedBigQueryDataTableResource_missesDataTableName_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId;

        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                new ReferencedBigQueryDataTableResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_NOTHING,
                    "testbq-projectid",
                    "testbq-datasetname",
                    ""));
      }

      @Test
      void createReferencedBigQueryDatasetResource_missesDatasetName_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        assertThrows(
            MissingRequiredFieldException.class,
            () ->
                new ReferencedBigQueryDatasetResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_NOTHING,
                    "testbq-projectid",
                    ""));
      }

      @Test
      void createReferencedBigQueryDataTableResource_invalidDataTableName_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId;

        assertThrows(
            InvalidNameException.class,
            () ->
                new ReferencedBigQueryDataTableResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_NOTHING,
                    "testbq-projectid",
                    DATASET_NAME,
                    "*&%@#"));
      }

      @Test
      void createReferencedBigQueryDatasetResource_invalidDatasetName_throwsException() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        assertThrows(
            InvalidReferenceException.class,
            () ->
                new ReferencedBigQueryDatasetResource(
                    workspaceId,
                    resourceId,
                    resourceName,
                    "description of " + resourceName,
                    CloningInstructions.COPY_NOTHING,
                    "testbq-projectid",
                    "Nor do datasets; neither ' nor *"));
      }

      @Test
      void testInvalidCast() {
        referenceResource = makeBigQueryDatasetResource();
        assertThrows(
            BadRequestException.class,
            () -> referenceResource.castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET));
        WsmResource wsmResource = referenceResource;
        assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
      }
    }

    @Nested
    class NegativeTests {

      @Test
      void getResourceById() {
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                referenceResourceService.getReferenceResource(
                    workspaceId, UUID.randomUUID(), USER_REQUEST));
      }

      @Test
      void getResourceByName() {
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                referenceResourceService.getReferenceResourceByName(
                    workspaceId, UUID.randomUUID().toString(), USER_REQUEST));
      }

      @Test
      void deleteResource() {
        referenceResourceService.deleteReferenceResource(
            workspaceId, UUID.randomUUID(), USER_REQUEST);
      }

      @Test
      void testDuplicateResourceName() {
        referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
        assertEquals(referenceResource.getStewardshipType(), StewardshipType.REFERENCED);

        referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

        UUID resourceId = UUID.randomUUID();
        ReferencedDataRepoSnapshotResource duplicateNameResource =
            new ReferencedDataRepoSnapshotResource(
                workspaceId,
                resourceId,
                referenceResource.getName(),
                null,
                CloningInstructions.COPY_NOTHING,
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
}
