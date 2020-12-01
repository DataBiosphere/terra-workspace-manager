package bio.terra.workspace.service.datareference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class DataReferenceServiceTest extends BaseUnitTest {

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

  @BeforeEach
  public void setup() {
    doReturn(true).when(mockDataRepoService).snapshotExists(any(), any(), any());
  }

  @Test
  public void testCreateDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    DataReferenceRequest request = defaultReferenceRequest(workspaceId).build();
    DataReference ref = dataReferenceService.createDataReference(request, USER_REQUEST);

    assertThat(ref.workspaceId(), equalTo(workspaceId));
    assertThat(ref.name(), equalTo(request.name()));
  }

  @Test
  public void testGetDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    DataReferenceRequest request = defaultReferenceRequest(workspaceId).build();
    UUID referenceId =
        dataReferenceService.createDataReference(request, USER_REQUEST).referenceId();
    DataReference ref =
        dataReferenceService.getDataReference(workspaceId, referenceId, USER_REQUEST);

    assertThat(ref.workspaceId(), equalTo(workspaceId));
    assertThat(ref.name(), equalTo(request.name()));
  }

  @Test
  public void testGetDataReferenceByName() {
    UUID workspaceId = createDefaultWorkspace();
    DataReferenceRequest request = defaultReferenceRequest(workspaceId).build();
    UUID referenceId =
        dataReferenceService.createDataReference(request, USER_REQUEST).referenceId();

    DataReference ref =
        dataReferenceService.getDataReferenceByName(
            workspaceId, request.referenceType(), request.name(), USER_REQUEST);

    assertThat(ref.workspaceId(), equalTo(workspaceId));
    assertThat(ref.name(), equalTo(request.name()));
  }

  @Test
  public void testGetMissingDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> dataReferenceService.getDataReference(workspaceId, UUID.randomUUID(), USER_REQUEST));
  }

  @Test
  public void enumerateDataReferences() {
    UUID workspaceId = createDefaultWorkspace();
    DataReferenceRequest firstRequest = defaultReferenceRequest(workspaceId).build();

    DataReference firstReference =
        dataReferenceService.createDataReference(firstRequest, USER_REQUEST);

    // Uses a different name because names are unique per reference type, per workspace.
    DataReferenceRequest secondRequest =
        defaultReferenceRequest(workspaceId).name("different_name").build();

    DataReference secondReference =
        dataReferenceService.createDataReference(secondRequest, USER_REQUEST);

    List<DataReference> result =
        dataReferenceService.enumerateDataReferences(workspaceId, 0, 10, USER_REQUEST);
    assertThat(result.size(), equalTo(2));
    assertThat(result, containsInAnyOrder(equalTo(firstReference), equalTo(secondReference)));
  }

  @Test
  public void enumerateFailsUnauthorized() {
    String samMessage = "Fake Sam unauthorized message";
    doThrow(new SamUnauthorizedException(samMessage))
        .when(mockSamService)
        .workspaceAuthz(any(), any(), any());
    UUID workspaceId = createDefaultWorkspace();
    assertThrows(
        SamUnauthorizedException.class,
        () -> dataReferenceService.enumerateDataReferences(workspaceId, 0, 10, USER_REQUEST));
  }

  @Test
  public void testDeleteDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    DataReferenceRequest request = defaultReferenceRequest(workspaceId).build();

    DataReference ref = dataReferenceService.createDataReference(request, USER_REQUEST);
    // Validate the reference exists and is readable.
    DataReference getReference =
        dataReferenceService.getDataReference(workspaceId, ref.referenceId(), USER_REQUEST);
    assertThat(getReference, equalTo(ref));

    dataReferenceService.deleteDataReference(workspaceId, ref.referenceId(), USER_REQUEST);
    // Validate that reference is now deleted.
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> dataReferenceService.getDataReference(workspaceId, ref.referenceId(), USER_REQUEST));
  }

  @Test
  public void testDeleteMissingDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    assertThrows(
        DataReferenceNotFoundException.class,
        () ->
            dataReferenceService.deleteDataReference(workspaceId, UUID.randomUUID(), USER_REQUEST));
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * RAWLS_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createDefaultWorkspace() {
    WorkspaceRequest request =
        WorkspaceRequest.builder()
            .workspaceId(UUID.randomUUID())
            .jobId(UUID.randomUUID().toString())
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    return workspaceService.createWorkspace(request, USER_REQUEST);
  }

  /**
   * Test utility providing a pre-filled ReferenceRequest.Builder with the provided workspaceId.
   *
   * <p>This gives a constant name, cloning instructions, and SnapshotReference as a reference
   * object.
   */
  private DataReferenceRequest.Builder defaultReferenceRequest(UUID workspaceId) {
    SnapshotReference snapshot = SnapshotReference.create("foo", "bar");
    return DataReferenceRequest.builder()
        .workspaceId(workspaceId)
        .name("some_name")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
        .referenceObject(snapshot);
  }
}
