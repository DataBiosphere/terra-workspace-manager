package bio.terra.workspace.service.datareference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.db.exception.InvalidDaoRequestException;
import bio.terra.workspace.generated.model.UpdateDataReferenceRequestBody;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.BigQueryDatasetReference;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.GoogleBucketReference;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class DataReferenceServiceTest extends BaseUnitTest {

  @Autowired private WorkspaceService workspaceService;
  @Autowired private DataReferenceService dataReferenceService;
  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  @MockBean private DataRepoService mockDataRepoService;

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  UUID workspaceId;
  DataReferenceRequest request;
  DataReference reference;

  Supplier<DataReference> currentReference =
      () ->
          dataReferenceService.getDataReference(workspaceId, reference.referenceId(), USER_REQUEST);

  @BeforeEach
  void setup() {
    doReturn(true).when(mockDataRepoService).snapshotExists(any(), any(), any());
    workspaceId = createDefaultWorkspace();
    request = defaultReferenceRequest(workspaceId, DataReferenceType.DATA_REPO_SNAPSHOT).build();
    reference = dataReferenceService.createDataReference(request, USER_REQUEST);
  }

  @Nested
  class CreateDataReference {

    @Test
    void testCreateDataReference() {
      assertThat(reference.workspaceId(), equalTo(workspaceId));
      assertThat(reference.name(), equalTo(request.name()));
    }

    @Test
    void testDefaultDataReferenceDescription() {
      DataReferenceRequest request =
          defaultReferenceRequest(workspaceId, DataReferenceType.DATA_REPO_SNAPSHOT)
              .name("another_name")
              .description(null)
              .build();
      DataReference reference = dataReferenceService.createDataReference(request, USER_REQUEST);

      assertThat(reference.description(), equalTo(""));
    }

    @Test
    void testCreateGoogleBucketReference() {
      UUID workspaceId = createDefaultWorkspace();
      DataReferenceRequest request =
          defaultReferenceRequest(workspaceId, DataReferenceType.GOOGLE_BUCKET).build();
      GoogleBucketReference requestReference = (GoogleBucketReference) request.referenceObject();
      DataReference ref = dataReferenceService.createDataReference(request, USER_REQUEST);

      assertThat(ref.workspaceId(), equalTo(workspaceId));
      assertThat(ref.name(), equalTo(request.name()));
      assertThat(ref.referenceType(), equalTo(DataReferenceType.GOOGLE_BUCKET));
      assertTrue(ref.referenceObject() instanceof GoogleBucketReference);
      GoogleBucketReference bucketReference = (GoogleBucketReference) ref.referenceObject();
      assertThat(bucketReference.bucketName(), equalTo(requestReference.bucketName()));
    }

    @Test
    void testCreateBigQueryDatasetReference() {
      UUID workspaceId = createDefaultWorkspace();
      DataReferenceRequest request =
          defaultReferenceRequest(workspaceId, DataReferenceType.BIG_QUERY_DATASET).build();
      BigQueryDatasetReference requestReference =
          (BigQueryDatasetReference) request.referenceObject();
      DataReference ref = dataReferenceService.createDataReference(request, USER_REQUEST);

      assertThat(ref.workspaceId(), equalTo(workspaceId));
      assertThat(ref.name(), equalTo(request.name()));
      assertThat(ref.referenceType(), equalTo(DataReferenceType.BIG_QUERY_DATASET));
      assertTrue(ref.referenceObject() instanceof BigQueryDatasetReference);
      BigQueryDatasetReference datasetReference = (BigQueryDatasetReference) ref.referenceObject();

      assertThat(datasetReference.projectId(), equalTo(requestReference.projectId()));
      assertThat(datasetReference.datasetName(), equalTo(requestReference.datasetName()));
    }
  }

  @Nested
  class GetDataReference {

    @Test
    void testGetDataReference() {
      DataReference ref = currentReference.get();

      assertThat(ref.workspaceId(), equalTo(workspaceId));
      assertThat(ref.name(), equalTo(request.name()));
    }

    @Test
    void testGetDataReferenceByWrongTypeMissing() {
      DataReference ref = currentReference.get();

      // Names are unique per reference type within a workspace.
      // This call looks up a GCS bucket with the snapshot's name, which should not exist.
      assertThrows(
          DataReferenceNotFoundException.class,
          () ->
              dataReferenceService.getDataReferenceByName(
                  workspaceId, DataReferenceType.GOOGLE_BUCKET, ref.name(), USER_REQUEST));
    }

    @Test
    void testGetDataReferenceByName() {
      DataReference ref =
          dataReferenceService.getDataReferenceByName(
              workspaceId, request.referenceType(), request.name(), USER_REQUEST);

      assertThat(ref.workspaceId(), equalTo(workspaceId));
      assertThat(ref.name(), equalTo(request.name()));
    }

    @Test
    void testGetMissingDataReference() {
      assertThrows(
          DataReferenceNotFoundException.class,
          () ->
              dataReferenceService.getDataReference(workspaceId, UUID.randomUUID(), USER_REQUEST));
    }
  }

  @Nested
  class EnumerateDataReferences {

    @Test
    void enumerateDataReferences() {
      // Uses a different name because names are unique per reference type, per workspace.
      DataReferenceRequest secondRequest =
          defaultReferenceRequest(workspaceId, DataReferenceType.DATA_REPO_SNAPSHOT)
              .name("different_name")
              .build();

      DataReference secondReference =
          dataReferenceService.createDataReference(secondRequest, USER_REQUEST);

      List<DataReference> result =
          dataReferenceService.enumerateDataReferences(workspaceId, 0, 10, USER_REQUEST);
      assertThat(result.size(), equalTo(2));
      assertThat(result, containsInAnyOrder(equalTo(reference), equalTo(secondReference)));
    }

    @Test
    void enumerateFailsUnauthorized() {
      String samMessage = "Fake Sam unauthorized message";
      doThrow(new SamUnauthorizedException(samMessage))
          .when(mockSamService)
          .workspaceAuthzOnly(any(), any(), any());

      assertThrows(
          SamUnauthorizedException.class,
          () -> dataReferenceService.enumerateDataReferences(workspaceId, 0, 10, USER_REQUEST));
    }
  }

  @Nested
  class UpdateDataReference {

    Consumer<UpdateDataReferenceRequestBody> updateReference =
        updateBody ->
            dataReferenceService.updateDataReference(
                workspaceId, reference.referenceId(), updateBody, USER_REQUEST);

    @Test
    void testUpdateName() {
      String updatedName = "rename";

      updateReference.accept(new UpdateDataReferenceRequestBody().name(updatedName));
      assertThat(currentReference.get().name(), equalTo(updatedName));
      assertThat(currentReference.get().description(), equalTo(reference.description()));
    }

    @Test
    void testUpdateDescription() {
      String updatedDescription = "updated description";

      updateReference.accept(new UpdateDataReferenceRequestBody().description(updatedDescription));
      assertThat(currentReference.get().name(), equalTo(reference.name()));
      assertThat(currentReference.get().description(), equalTo(updatedDescription));
    }

    @Test
    void testUpdateAllFields() {
      String updatedName2 = "rename_again";
      String updatedDescription2 = "updated description again";

      updateReference.accept(
          new UpdateDataReferenceRequestBody().name(updatedName2).description(updatedDescription2));
      assertThat(currentReference.get().name(), equalTo(updatedName2));
      assertThat(currentReference.get().description(), equalTo(updatedDescription2));
    }

    @Test
    void updateNothingFails() {
      assertThrows(
          InvalidDaoRequestException.class,
          () -> updateReference.accept(new UpdateDataReferenceRequestBody()));
    }
  }

  @Nested
  class DeleteDataReference {

    @Test
    void testDeleteDataReference() {
      // Validate the reference exists and is readable.
      assertThat(currentReference.get(), equalTo(reference));

      dataReferenceService.deleteDataReference(workspaceId, reference.referenceId(), USER_REQUEST);
      // Validate that reference is now deleted.
      assertThrows(DataReferenceNotFoundException.class, () -> currentReference.get());
    }

    @Test
    void testDeleteMissingDataReference() {
      assertThrows(
          DataReferenceNotFoundException.class,
          () ->
              dataReferenceService.deleteDataReference(
                  workspaceId, UUID.randomUUID(), USER_REQUEST));
    }
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * MC_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createDefaultWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .jobId(UUID.randomUUID().toString())
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, USER_REQUEST);
  }

  /**
   * Test utility providing a pre-filled ReferenceRequest.Builder with the provided workspaceId.
   *
   * <p>This gives a constant name, cloning instructions, and referenceObject of specified type.
   */
  private DataReferenceRequest.Builder defaultReferenceRequest(
      UUID workspaceId, DataReferenceType type) {
    final ReferenceObject referenceObject;
    switch (type) {
      case DATA_REPO_SNAPSHOT:
        referenceObject = SnapshotReference.create("fake-instance-name", "snapshot-name");
        break;
      case GOOGLE_BUCKET:
        referenceObject = GoogleBucketReference.create("my-bucket-name");
        break;
      case BIG_QUERY_DATASET:
        referenceObject = BigQueryDatasetReference.create("my-project", "dataset_id");
        break;
      default:
        throw new InvalidDataReferenceException(
            "Invalid reference type specified in DataReferenceServiceTest");
    }
    return DataReferenceRequest.builder()
        .workspaceId(workspaceId)
        .name("some_name")
        .description("some description, too")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .referenceType(type)
        .referenceObject(referenceObject);
  }
}
