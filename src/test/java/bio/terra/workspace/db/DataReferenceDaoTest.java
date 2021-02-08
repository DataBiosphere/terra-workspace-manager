package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

public class DataReferenceDaoTest extends BaseUnitTest {

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;

  @Autowired private DataReferenceDao dataReferenceDao;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ObjectMapper objectMapper;

  @Test
  public void verifyCreatedDataReferenceExists() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);
    DataReference reference = dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(reference.referenceId(), equalTo(referenceId));
  }

  @Test
  public void createReferenceWithoutWorkspaceFails() {
    // This reference uses a random workspaceID, so the corresponding workspace does not exist.
    DataReferenceRequest referenceRequest = defaultReferenceRequest(UUID.randomUUID()).build();
    assertThrows(
        DataIntegrityViolationException.class,
        () -> dataReferenceDao.createDataReference(referenceRequest, UUID.randomUUID()));
  }

  @Test
  public void verifyCreateDuplicateNameFails() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    assertThrows(
        DuplicateDataReferenceException.class,
        () -> dataReferenceDao.createDataReference(referenceRequest, referenceId));
  }

  @Test
  public void verifyGetDataReferenceByName() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    DataReference ref =
        dataReferenceDao.getDataReferenceByName(
            workspaceId, referenceRequest.referenceType(), referenceRequest.name());
    assertThat(ref.referenceId(), equalTo(referenceId));
  }

  @Test
  public void verifyGetDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    DataReference result = dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(result.workspaceId(), equalTo(workspaceId));
    assertThat(result.referenceId(), equalTo(referenceId));
    assertThat(result.name(), equalTo(referenceRequest.name()));
    assertThat(result.referenceType(), equalTo(referenceRequest.referenceType()));
    assertThat(result.referenceObject(), equalTo(referenceRequest.referenceObject()));
  }

  @Test
  public void verifyGetDataReferenceNotInWorkspaceNotFound() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    Workspace decoyWorkspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    UUID decoyId = workspaceDao.createWorkspace(decoyWorkspace);
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> dataReferenceDao.getDataReference(decoyId, referenceId));
  }

  @Test
  public void verifyUpdateDataReference() {
    String updatedName = "rename";
    String updatedName2 = "rename_again";
    String updatedDescription = "updated description";
    String updatedDescription2 = "updated description again";

    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();

    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    Supplier<DataReference> currentReference =
        () -> dataReferenceDao.getDataReference(workspaceId, referenceId);

    BiFunction<String, String, Boolean> updateReference =
        (String name, String description) ->
            dataReferenceDao.updateDataReference(workspaceId, referenceId, name, description);

    // Validate the reference exists and is readable.
    assertThat(currentReference.get().referenceId(), equalTo(referenceId));

    // Update name only
    updateReference.apply(updatedName, null);
    DataReference updatedNameReference1 = currentReference.get();
    assertThat(updatedNameReference1.name(), equalTo(updatedName));
    assertThat(
        updatedNameReference1.referenceDescription(),
        equalTo(referenceRequest.referenceDescription()));

    // Update description only
    updateReference.apply(null, updatedDescription);
    DataReference updatedDescriptionReference = currentReference.get();
    assertThat(updatedDescriptionReference.name(), equalTo(updatedNameReference1.name()));
    assertThat(updatedDescriptionReference.referenceDescription(), equalTo(updatedDescription));

    // Update both
    updateReference.apply(updatedName2, updatedDescription2);
    DataReference updatedBothReference = currentReference.get();
    assertThat(updatedBothReference.name(), equalTo(updatedName2));
    assertThat(updatedBothReference.referenceDescription(), equalTo(updatedDescription2));
  }

  @Test
  public void verifyDeleteDataReference() {
    UUID workspaceId = createDefaultWorkspace();
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    assertTrue(dataReferenceDao.deleteDataReference(workspaceId, referenceId));

    // try to delete again to make sure it's not there
    assertFalse(dataReferenceDao.deleteDataReference(workspaceId, referenceId));
  }

  @Test
  public void deleteNonExistentWorkspaceFails() {
    assertFalse(dataReferenceDao.deleteDataReference(UUID.randomUUID(), UUID.randomUUID()));
  }

  @Test
  public void enumerateWorkspaceReferences() {
    UUID workspaceId = createDefaultWorkspace();
    UUID firstReferenceId = UUID.randomUUID();
    DataReferenceRequest firstRequest = defaultReferenceRequest(workspaceId).build();

    // Create two references in the same workspace.
    dataReferenceDao.createDataReference(firstRequest, firstReferenceId);
    DataReference firstReference = dataReferenceDao.getDataReference(workspaceId, firstReferenceId);

    // This needs a non-default name as we enforce name uniqueness per type per workspace.
    DataReferenceRequest secondRequest = defaultReferenceRequest(workspaceId).name("bar").build();
    UUID secondReferenceId = UUID.randomUUID();
    dataReferenceDao.createDataReference(secondRequest, secondReferenceId);
    DataReference secondReference =
        dataReferenceDao.getDataReference(workspaceId, secondReferenceId);

    // Validate that both DataReferences are enumerated
    List<DataReference> enumerateResult =
        dataReferenceDao.enumerateDataReferences(workspaceId, 0, 10);
    assertThat(enumerateResult.size(), equalTo(2));
    assertThat(
        enumerateResult, containsInAnyOrder(equalTo(firstReference), equalTo(secondReference)));
  }

  @Test
  public void enumerateEmptyReferenceList() {
    UUID workspaceId = createDefaultWorkspace();

    List<DataReference> result = dataReferenceDao.enumerateDataReferences(workspaceId, 0, 10);
    assertTrue(result.isEmpty());
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * RAWLS_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createDefaultWorkspace() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    return workspaceDao.createWorkspace(workspace);
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
        .referenceDescription(null)
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
        .referenceObject(snapshot);
  }

  // TODO: currently no tests enumerating controlled data resources, as we have no way to create
  // them.
}
