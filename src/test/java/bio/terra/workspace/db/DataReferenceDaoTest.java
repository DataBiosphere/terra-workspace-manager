package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DataReferenceNotFoundException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

public class DataReferenceDaoTest extends BaseUnitTest {

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;

  @Autowired private DataReferenceDao dataReferenceDao;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ObjectMapper objectMapper;

  private UUID referenceId;

  @BeforeEach
  public void setup() {
    referenceId = UUID.randomUUID();
  }

  @Test
  public void verifyCreatedDataReferenceExists() {
    workspaceDao.createWorkspace(defaultWorkspace());
    DataReferenceRequest referenceRequest = defaultReference();

    dataReferenceDao.createDataReference(
        referenceRequest,
        referenceId);
    DataReference reference =
        dataReferenceDao.getDataReference(referenceRequest.workspaceId(), referenceId);

    assertThat(reference.referenceId(), equalTo(referenceId));
  }

  @Test
  public void createReferenceWithoutWorkspaceFails() {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          dataReferenceDao.createDataReference(
              defaultReference(),
              referenceId);
        });
  }

  @Test
  public void verifyCreateDuplicateNameFails() {
    workspaceDao.createWorkspace(defaultWorkspace());
    DataReferenceRequest referenceRequest = defaultReference();

    dataReferenceDao.createDataReference(
        referenceRequest,
        referenceId);

    assertThrows(
        DuplicateDataReferenceException.class,
        () -> {
          dataReferenceDao.createDataReference(
              referenceRequest,
              referenceId);
        });
  }

  @Test
  public void verifyGetDataReferenceByName() {
    workspaceDao.createWorkspace(defaultWorkspace());
    DataReferenceRequest referenceRequest = defaultReference();

    dataReferenceDao.createDataReference(
        referenceRequest,
        referenceId);

    DataReference ref =
        dataReferenceDao.getDataReferenceByName(referenceRequest.workspaceId(), referenceRequest.referenceType(), referenceRequest.name());
    assertThat(ref.referenceId(), equalTo(referenceId));
  }

  @Test
  public void verifyGetDataReference() {
    workspaceDao.createWorkspace(defaultWorkspace());

    dataReferenceDao.createDataReference(
        referenceRequest,
        referenceId);

    DataReference result = dataReferenceDao.getDataReference(referenceRequest.workspaceId(), referenceId);

    assertThat(result.workspaceId(), equalTo(referenceRequest.workspaceId()));
    assertThat(result.referenceId(), equalTo(referenceId));
    assertThat(result.name(), equalTo(referenceRequest.name()));
    assertThat(result.referenceType(), equalTo(referenceRequest.referenceType()));
    assertThat(result.referenceObject(), equalTo(referenceRequest.referenceObject()));
    //    assertThat(result.getReference().getInstance(), equalTo(reference.getInstance()));
  }

  @Test
  public void verifyGetDataReferenceNotInWorkspaceNotFound() {
    workspaceDao.createWorkspace(defaultWorkspace());

    Workspace decoyWorkspace =
        Workspace.builder()
            .workspaceId(UUID.randomUUID())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(decoyWorkspace);
    referenceRequest.set

    dataReferenceDao.createDataReference(
        referenceId,
        decoyWorkspace.workspaceId(),
        name,
        null,
        credentialId,
        cloningInstructions,
        referenceType,
        reference);

    assertThrows(
        DataReferenceNotFoundException.class,
        () -> {
          dataReferenceDao.getDataReference(workspaceId, referenceId);
        });
  }

  @Test
  public void verifyDeleteDataReference() {
    workspaceDao.createWorkspace(defaultWorkspace());

    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        name,
        null,
        credentialId,
        cloningInstructions,
        referenceType,
        reference);

    assertTrue(dataReferenceDao.deleteDataReference(workspaceId, referenceId));

    // try to delete again to make sure it's not there
    assertFalse(dataReferenceDao.deleteDataReference(workspaceId, referenceId));
  }

  @Test
  public void deleteNonExistentWorkspaceFails() throws Exception {
    assertFalse(dataReferenceDao.deleteDataReference(workspaceId, referenceId));
  }

  @Test
  public void enumerateWorkspaceReferences() throws Exception {
    workspaceDao.createWorkspace(defaultWorkspace());

    // Create two references in the same workspace.
    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        name,
        null,
        credentialId,
        cloningInstructions,
        referenceType,
        reference);
    DataReferenceDescription firstReference =
        dataReferenceDao.getDataReference(workspaceId, referenceId);

    UUID secondReferenceId = UUID.randomUUID();
    dataReferenceDao.createDataReference(
        secondReferenceId,
        workspaceId,
        name + "2",
        null,
        credentialId,
        cloningInstructions,
        referenceType,
        reference);
    DataReferenceDescription secondReference =
        dataReferenceDao.getDataReference(workspaceId, secondReferenceId);

    // Validate that both DataReferences are enumerated
    DataReferenceList enumerateResult =
        dataReferenceDao.enumerateDataReferences(workspaceId, name, 0, 10);
    assertThat(enumerateResult.getResources().size(), equalTo(2));
    assertThat(
        enumerateResult.getResources(),
        containsInAnyOrder(equalTo(firstReference), equalTo(secondReference)));
  }

  @Test
  public void enumerateEmptyReferenceList() throws Exception {
    workspaceDao.createWorkspace(defaultWorkspace());

    DataReferenceList result = dataReferenceDao.enumerateDataReferences(workspaceId, name, 0, 10);
    assertThat(result.getResources(), empty());
  }

  private String objectToString(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Invalid data reference");
    }
  }

  private Workspace defaultWorkspace() {
    return Workspace.builder()
        .workspaceId(workspaceId)
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
        .build();
  }

  private DataReferenceRequest defaultReference() {
    return defaultReferenceBuilder().build();
  }

  private DataReferenceRequest.Builder defaultReferenceBuilder() {
    SnapshotReference snapshot = new SnapshotReference(UUID.randomUUID().toString(), UUID.randomUUID().toString());
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
