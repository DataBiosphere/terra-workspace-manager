package bio.terra.workspace.db;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.db.exception.InvalidDaoRequestException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataReferenceDaoTest extends BaseUnitTest {

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;

  @Autowired private DataReferenceDao dataReferenceDao;
  @Autowired private WorkspaceDao workspaceDao;

  UUID workspaceId;
  UUID referenceId;
  DataReferenceRequest referenceRequest;

  Supplier<DataReference> currentReference =
      () -> dataReferenceDao.getDataReference(workspaceId, referenceId);

  @BeforeEach
  void setup() {
    workspaceId = createDefaultWorkspace();
    referenceId = UUID.randomUUID();
    referenceRequest = defaultReferenceRequest(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);
  }

  @Nested
  class CreateDataReference {

    @Test
    void verifyCreatedDataReferenceExists() {
      assertThat(currentReference.get().referenceId(), equalTo(referenceId));
    }

    @Test
    void createReferenceWithoutWorkspaceFails() {
      // This reference uses a random workspaceID, so the corresponding workspace does not exist.
      DataReferenceRequest referenceRequest = defaultReferenceRequest(UUID.randomUUID()).build();
      assertThrows(
          DataIntegrityViolationException.class,
          () -> dataReferenceDao.createDataReference(referenceRequest, UUID.randomUUID()));
    }

    @Test
    void verifyCreateDuplicateNameFails() {
      assertThrows(
          DuplicateDataReferenceException.class,
          () -> dataReferenceDao.createDataReference(referenceRequest, referenceId));
    }
  }

  @Nested
  class GetDataReference {

    @Test
    void verifyGetDataReferenceByName() {
      DataReference ref =
          dataReferenceDao.getDataReferenceByName(
              workspaceId, referenceRequest.referenceType(), referenceRequest.name());
      assertThat(ref.referenceId(), equalTo(referenceId));
    }

    @Test
    void verifyGetDataReference() {
      DataReference result = currentReference.get();

      assertThat(result.workspaceId(), equalTo(workspaceId));
      assertThat(result.referenceId(), equalTo(referenceId));
      assertThat(result.name(), equalTo(referenceRequest.name()));
      assertThat(result.referenceType(), equalTo(referenceRequest.referenceType()));
      assertThat(result.referenceObject(), equalTo(referenceRequest.referenceObject()));
    }

    @Test
    void verifyGetDataReferenceNotInWorkspaceNotFound() {
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
  }

  @Nested
  class UpdateDataReference {

    BiFunction<String, String, Boolean> updateReference =
        (String name, String description) ->
            dataReferenceDao.updateDataReference(workspaceId, referenceId, name, description);

    @Test
    void verifyUpdateName() {
      String updatedName = "rename";

      updateReference.apply(updatedName, null);
      assertThat(currentReference.get().name(), equalTo(updatedName));
      assertThat(currentReference.get().description(), equalTo(referenceRequest.description()));
    }

    @Test
    void verifyUpdateDescription() {
      String updatedDescription = "updated description";

      updateReference.apply(null, updatedDescription);
      assertThat(currentReference.get().name(), equalTo(referenceRequest.name()));
      assertThat(currentReference.get().description(), equalTo(updatedDescription));
    }

    @Test
    void verifyUpdateAllFields() {
      String updatedName2 = "rename_again";
      String updatedDescription2 = "updated description again";

      updateReference.apply(updatedName2, updatedDescription2);
      assertThat(currentReference.get().name(), equalTo(updatedName2));
      assertThat(currentReference.get().description(), equalTo(updatedDescription2));
    }

    @Test
    void updateNothingFails() {
      assertThrows(InvalidDaoRequestException.class, () -> updateReference.apply(null, null));
    }
  }

  @Nested
  class DeleteDataReference {

    @Test
    void verifyDeleteDataReference() {
      assertTrue(dataReferenceDao.deleteDataReference(workspaceId, referenceId));

      // try to delete again to make sure it's not there
      assertFalse(dataReferenceDao.deleteDataReference(workspaceId, referenceId));
    }

    @Test
    void deleteNonExistentWorkspaceFails() {
      assertFalse(dataReferenceDao.deleteDataReference(UUID.randomUUID(), UUID.randomUUID()));
    }
  }

  @Nested
  class EnumerateDataReferences {

    @Test
    void enumerateWorkspaceReferences() {
      // Create two references in the same workspace.
      DataReference firstReference = currentReference.get();

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
    void enumerateEmptyReferenceList() {
      UUID newWorkspaceId = createDefaultWorkspace();

      List<DataReference> result = dataReferenceDao.enumerateDataReferences(newWorkspaceId, 0, 10);
      assertTrue(result.isEmpty());
    }
  }

  /**
   * Test utility which creates a workspace with a random ID, no spend profile, and stage
   * RAWLS_WORKSPACE. Returns the generated workspace ID.
   */
  private UUID createDefaultWorkspace() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .spendProfileId(null)
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
        .description("some description, too")
        .cloningInstructions(CloningInstructions.COPY_NOTHING)
        .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
        .referenceObject(snapshot);
  }

  // TODO: currently no tests enumerating controlled data resources, as we have no way to create
  // them.
}
