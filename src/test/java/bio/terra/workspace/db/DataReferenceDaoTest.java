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
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.SnapshotReference;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
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
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);
    DataReference reference = dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(reference.referenceId(), equalTo(referenceId));
  }

  @Test
  public void createReferenceWithoutWorkspaceFails() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          dataReferenceDao.createDataReference(
              defaultReferenceBuilder().build(), UUID.randomUUID());
        });
  }

  @Test
  public void verifyCreateDuplicateNameFails() {
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    assertThrows(
        DuplicateDataReferenceException.class,
        () -> {
          dataReferenceDao.createDataReference(referenceRequest, referenceId);
        });
  }

  @Test
  public void verifyGetDataReferenceByName() {
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    DataReference ref =
        dataReferenceDao.getDataReferenceByName(
            workspaceId, referenceRequest.referenceType(), referenceRequest.name());
    assertThat(ref.referenceId(), equalTo(referenceId));
  }

  @Test
  public void verifyGetDataReference() {
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    DataReference result = dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(result.workspaceId(), equalTo(workspaceId));
    assertThat(result.referenceId(), equalTo(referenceId));
    assertThat(result.name(), equalTo(referenceRequest.name()));
    assertThat(result.referenceType(), equalTo(referenceRequest.referenceType()));
    // Compare properties rather than the referenceObjects themselves, as the interface does not
    // require
    // a reasonable equals() method.
    assertThat(
        result.referenceObject().getProperties(),
        equalTo(referenceRequest.referenceObject().getProperties()));
  }

  @Test
  public void verifyGetDataReferenceNotInWorkspaceNotFound() {
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
    dataReferenceDao.createDataReference(referenceRequest, referenceId);

    Workspace decoyWorkspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    UUID decoyId = workspaceDao.createWorkspace(decoyWorkspace);
    assertThrows(
        DataReferenceNotFoundException.class,
        () -> {
          dataReferenceDao.getDataReference(decoyId, referenceId);
        });
  }

  @Test
  public void verifyDeleteDataReference() {
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID referenceId = UUID.randomUUID();
    DataReferenceRequest referenceRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).build();
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
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());
    UUID firstReferenceId = UUID.randomUUID();
    DataReferenceRequest firstRequest = defaultReferenceBuilder().workspaceId(workspaceId).build();

    // Create two references in the same workspace.
    dataReferenceDao.createDataReference(firstRequest, firstReferenceId);
    DataReference firstReference = dataReferenceDao.getDataReference(workspaceId, firstReferenceId);

    // This needs a non-default name as we enforce name uniqueness per type per workspace.
    DataReferenceRequest secondRequest =
        defaultReferenceBuilder().workspaceId(workspaceId).name("bar").build();
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
    UUID workspaceId = workspaceDao.createWorkspace(defaultWorkspace());

    List<DataReference> result = dataReferenceDao.enumerateDataReferences(workspaceId, 0, 10);
    assertTrue(result.isEmpty());
  }

  private String objectToString(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Invalid data reference");
    }
  }

  private static Workspace defaultWorkspace() {
    return Workspace.builder()
        .workspaceId(UUID.randomUUID())
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
        .build();
  }

  private static DataReferenceRequest.Builder defaultReferenceBuilder() {
    SnapshotReference snapshot =
        SnapshotReference.create(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    return DataReferenceRequest.builder()
        .workspaceId(UUID.randomUUID())
        .name("this_is_a_name")
        .referenceType(DataReferenceType.DATA_REPO_SNAPSHOT)
        .referenceObject(snapshot)
        .cloningInstructions(CloningInstructions.COPY_NOTHING);
  }

  // TODO: currently no tests enumerating controlled data resources, as we have no way to create
  // them.
}
