package bio.terra.workspace.service.resource.referenced;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.fixtures.ReferenceResourceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.InvalidDaoRequestException;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.ArrayList;
import java.util.List;
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
class ReferenceResourceServiceTest extends BaseUnitTest {
  private static final Logger logger = LoggerFactory.getLogger(ReferenceResourceServiceTest.class);
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
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  @MockBean private DataRepoService mockDataRepoService;
  @MockBean private CrlService mockCrlService;

  private UUID workspaceId;
  private ReferencedResource referenceResource;

  @BeforeEach
  void setup() {
    doReturn(true).when(mockDataRepoService).snapshotExists(any(), any(), any());
    workspaceId = createMcTestWorkspace();
    referenceResource = null;
  }

  @AfterEach
  void teardown() {
    if (referenceResource != null) {
      try {
        referenceResourceService.deleteReferenceResource(
            referenceResource.getWorkspaceId(), referenceResource.getResourceId(), USER_REQUEST);
      } catch (Exception ex) {
        logger.warn("Failed to delete reference resource " + referenceResource.getResourceId());
      }
    }
    workspaceDao.deleteWorkspace(workspaceId);
  }

  @Test
  void testUpdate() {
    referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
    referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);

    // Change the name
    String updatedName = "renamed" + referenceResource.getName();
    String originalDescription = referenceResource.getDescription();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), updatedName, null, USER_REQUEST);
    referenceResource =
        referenceResourceService.getReferenceResourceByName(workspaceId, updatedName, USER_REQUEST);
    assertThat(referenceResource.getName(), equalTo(updatedName));
    assertThat(referenceResource.getDescription(), equalTo(originalDescription));

    // Change the description
    String updatedDescription = "updated" + referenceResource.getDescription();

    referenceResourceService.updateReferenceResource(
        workspaceId, referenceResource.getResourceId(), null, updatedDescription, USER_REQUEST);
    referenceResource =
        referenceResourceService.getReferenceResource(
            workspaceId, referenceResource.getResourceId(), USER_REQUEST);
    assertThat(referenceResource.getName(), equalTo(updatedName));
    assertThat(referenceResource.getDescription(), equalTo(updatedDescription));

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
    assertThat(referenceResource.getName(), equalTo(updatedName2));
    assertThat(referenceResource.getDescription(), equalTo(updatedDescription2));

    // Change nothing and see the error
    assertThrows(
        InvalidDaoRequestException.class,
        () ->
            referenceResourceService.updateReferenceResource(
                workspaceId, referenceResource.getResourceId(), null, null, USER_REQUEST));
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * MC_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createMcTestWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .jobId(UUID.randomUUID().toString())
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, USER_REQUEST);
  }

  @Nested
  class WsmValidityChecks {
    // Test that all of the WsmResource validity checks catch invalid input

    @Test
    void testInvalidWorkspaceId() {
      referenceResource =
          new ReferencedDataRepoSnapshotResource(
              null,
              UUID.randomUUID(),
              "aname",
              null,
              CloningInstructions.COPY_NOTHING,
              DATA_REPO_INSTANCE_NAME,
              "polaroid");
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST));
    }

    @Test
    void testInvalidName() {
      referenceResource =
          new ReferencedDataRepoSnapshotResource(
              workspaceId,
              UUID.randomUUID(),
              null,
              null,
              CloningInstructions.COPY_NOTHING,
              DATA_REPO_INSTANCE_NAME,
              "polaroid");
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST));
    }

    @Test
    void testInvalidCloningInstructions() {
      referenceResource =
          new ReferencedDataRepoSnapshotResource(
              workspaceId,
              UUID.randomUUID(),
              "aname",
              null,
              null,
              DATA_REPO_INSTANCE_NAME,
              "polaroid");
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST));
    }

    @Test
    void testInvalidResourceId() {
      referenceResource =
          new ReferencedDataRepoSnapshotResource(
              workspaceId, null, "aname", null, null, DATA_REPO_INSTANCE_NAME, "polaroid");
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST));
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
      assertThat(referenceResource.getStewardshipType(), equalTo(StewardshipType.REFERENCED));

      ReferencedDataRepoSnapshotResource resource =
          referenceResource.castToDataRepoSnapshotResource();
      assertThat(resource.getResourceType(), equalTo(WsmResourceType.DATA_REPO_SNAPSHOT));

      ReferencedResource resultReferenceResource =
          referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
      ReferencedDataRepoSnapshotResource resultResource =
          resultReferenceResource.castToDataRepoSnapshotResource();
      assertThat(resource, equalTo(resultResource));

      ReferencedResource byid =
          referenceResourceService.getReferenceResource(
              workspaceId, resource.getResourceId(), USER_REQUEST);
      ReferencedResource byname =
          referenceResourceService.getReferenceResourceByName(
              workspaceId, resource.getName(), USER_REQUEST);
      assertThat(
          byid.castToDataRepoSnapshotResource(), equalTo(byname.castToDataRepoSnapshotResource()));

      referenceResourceService.deleteReferenceResource(
          workspaceId, referenceResource.getResourceId(), USER_REQUEST);
    }

    @Test
    void testInvalidInstanceName() {
      UUID resourceId = UUID.randomUUID();
      String resourceName = "testdatarepo-" + resourceId.toString();

      ReferencedDataRepoSnapshotResource resource =
          new ReferencedDataRepoSnapshotResource(
              workspaceId,
              resourceId,
              resourceName,
              "description of " + resourceName,
              CloningInstructions.COPY_NOTHING,
              null,
              "polaroid");
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(resource, USER_REQUEST));
    }

    @Test
    void testInvalidSnapshot() {
      UUID resourceId = UUID.randomUUID();
      String resourceName = "testdatarepo-" + resourceId.toString();

      ReferencedDataRepoSnapshotResource resource =
          new ReferencedDataRepoSnapshotResource(
              workspaceId,
              resourceId,
              resourceName,
              "description of " + resourceName,
              CloningInstructions.COPY_NOTHING,
              DATA_REPO_INSTANCE_NAME,
              null);
      assertThrows(
          MissingRequiredFieldException.class,
          () -> referenceResourceService.createReferenceResource(resource, USER_REQUEST));
    }

    @Test
    void testInvalidCast() {
      referenceResource = ReferenceResourceFixtures.makeDataRepoSnapshotResource(workspaceId);
      assertThrows(
          InvalidMetadataException.class, () -> referenceResource.castToBigQueryDatasetResource());

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
        assertThat(daoResources.size(), equalTo(resources.size()));
      } finally {
        for (var resource : resources) {
          referenceResourceService.deleteReferenceResource(
              workspaceId, resource.getResourceId(), USER_REQUEST);
        }
      }
    }

    @Nested
    class GcpBucketReference {
      @BeforeEach
      void setup() throws Exception {
        // Make the Verify step always succeed
        doReturn(true).when(mockCrlService).gcsBucketExists(any(), any());
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
        assertThat(referenceResource.getStewardshipType(), equalTo(StewardshipType.REFERENCED));

        ReferencedGcsBucketResource resource = referenceResource.castToGcsBucketResource();
        assertThat(resource.getResourceType(), equalTo(WsmResourceType.GCS_BUCKET));

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
        ReferencedGcsBucketResource resultResource =
            resultReferenceResource.castToGcsBucketResource();
        assertThat(resource, equalTo(resultResource));

        ReferencedResource byid =
            referenceResourceService.getReferenceResource(
                workspaceId, resource.getResourceId(), USER_REQUEST);
        ReferencedResource byname =
            referenceResourceService.getReferenceResourceByName(
                workspaceId, resource.getName(), USER_REQUEST);
        assertThat(byid.castToGcsBucketResource(), equalTo(byname.castToGcsBucketResource()));

        referenceResourceService.deleteReferenceResource(
            workspaceId, referenceResource.getResourceId(), USER_REQUEST);
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
            InvalidReferenceException.class,
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
            InvalidMetadataException.class,
            () -> referenceResource.castToBigQueryDatasetResource());

        WsmResource wsmResource = referenceResource;
        assertThrows(InvalidMetadataException.class, wsmResource::castToControlledResource);
      }
    }

    @Nested
    class BigQueryReference {
      private static final String DATASET_NAME = "testbq_datasetname";

      @BeforeEach
      void setup() throws Exception {
        // Make the Verify step always succeed
        doReturn(true).when(mockCrlService).bigQueryDatasetExists(any(), any(), any());
      }

      private ReferencedBigQueryDatasetResource makeBigQueryResource() {
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

      @Test
      void testBigQueryReference() {
        referenceResource = makeBigQueryResource();
        assertThat(referenceResource.getStewardshipType(), equalTo(StewardshipType.REFERENCED));

        ReferencedBigQueryDatasetResource resource =
            referenceResource.castToBigQueryDatasetResource();
        assertThat(resource.getResourceType(), equalTo(WsmResourceType.BIG_QUERY_DATASET));

        ReferencedResource resultReferenceResource =
            referenceResourceService.createReferenceResource(referenceResource, USER_REQUEST);
        ReferencedBigQueryDatasetResource resultResource =
            resultReferenceResource.castToBigQueryDatasetResource();
        assertThat(resource, equalTo(resultResource));

        ReferencedResource byid =
            referenceResourceService.getReferenceResource(
                workspaceId, resource.getResourceId(), USER_REQUEST);
        ReferencedResource byname =
            referenceResourceService.getReferenceResourceByName(
                workspaceId, resource.getName(), USER_REQUEST);
        assertThat(
            byid.castToBigQueryDatasetResource(), equalTo(byname.castToBigQueryDatasetResource()));

        referenceResourceService.deleteReferenceResource(
            workspaceId, referenceResource.getResourceId(), USER_REQUEST);
      }

      @Test
      void testMissingProjectId() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        ReferencedBigQueryDatasetResource resource =
            new ReferencedBigQueryDatasetResource(
                workspaceId,
                resourceId,
                resourceName,
                "description of " + resourceName,
                CloningInstructions.COPY_NOTHING,
                null,
                "testbq-datasetname");
        assertThrows(
            MissingRequiredFieldException.class,
            () -> referenceResourceService.createReferenceResource(resource, USER_REQUEST));
      }

      @Test
      void testMissingDatasetName() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        ReferencedBigQueryDatasetResource resource =
            new ReferencedBigQueryDatasetResource(
                workspaceId,
                resourceId,
                resourceName,
                "description of " + resourceName,
                CloningInstructions.COPY_NOTHING,
                "testbq-projectid",
                "");
        assertThrows(
            MissingRequiredFieldException.class,
            () -> referenceResourceService.createReferenceResource(resource, USER_REQUEST));
      }

      @Test
      void testInvalidDatasetName() {
        UUID resourceId = UUID.randomUUID();
        String resourceName = "testdatarepo-" + resourceId.toString();

        ReferencedBigQueryDatasetResource resource =
            new ReferencedBigQueryDatasetResource(
                workspaceId,
                resourceId,
                resourceName,
                "description of " + resourceName,
                CloningInstructions.COPY_NOTHING,
                "testbq-projectid",
                "Nor do datasets; neither ' nor *");
        assertThrows(
            InvalidReferenceException.class,
            () -> referenceResourceService.createReferenceResource(resource, USER_REQUEST));
      }

      @Test
      void testInvalidCast() {
        referenceResource = makeBigQueryResource();
        assertThrows(
            InvalidMetadataException.class, () -> referenceResource.castToGcsBucketResource());

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
        assertThat(referenceResource.getStewardshipType(), equalTo(StewardshipType.REFERENCED));

        ReferencedDataRepoSnapshotResource resource =
            referenceResource.castToDataRepoSnapshotResource();
        assertThat(resource.getResourceType(), equalTo(WsmResourceType.DATA_REPO_SNAPSHOT));
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
