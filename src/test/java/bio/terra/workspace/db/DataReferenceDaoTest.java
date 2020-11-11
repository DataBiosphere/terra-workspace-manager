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
import bio.terra.workspace.common.model.Workspace;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.generated.model.CloningInstructionsEnum;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
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

  private UUID workspaceId;
  private UUID referenceId;
  private String name;
  private ReferenceTypeEnum referenceType;
  private DataRepoSnapshot reference;
  private String credentialId;
  private UUID resourceId;
  private CloningInstructionsEnum cloningInstructions;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    referenceId = UUID.randomUUID();
    name = "this_is_a_name";
    referenceType = ReferenceTypeEnum.DATA_REPO_SNAPSHOT;

    reference = new DataRepoSnapshot();
    reference.setInstanceName(UUID.randomUUID().toString());
    reference.setSnapshot(UUID.randomUUID().toString());

    credentialId = UUID.randomUUID().toString();
    resourceId =
        null; // eventually we will more thoroughly support controlled resources, so this won't
    // always be null
    cloningInstructions = CloningInstructionsEnum.NOTHING;
  }

  @Test
  public void verifyCreatedDataReferenceExists() {
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
    DataReferenceDescription reference =
        dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(reference.getReferenceId(), equalTo(referenceId));
  }

  @Test
  public void createReferenceWithoutWorkspaceFails() throws Exception {
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          dataReferenceDao.createDataReference(
              referenceId,
              UUID.randomUUID(), // non-existing workspace ID
              name,
              null,
              credentialId,
              cloningInstructions,
              referenceType,
              reference);
        });
  }

  @Test
  public void verifyCreateDuplicateNameFails() throws Exception {
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

    assertThrows(
        DuplicateDataReferenceException.class,
        () -> {
          dataReferenceDao.createDataReference(
              referenceId,
              workspaceId,
              name,
              null,
              credentialId,
              cloningInstructions,
              referenceType,
              reference);
        });
  }

  @Test
  public void verifyGetDataReferenceByName() {
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

    DataReferenceDescription ref =
        dataReferenceDao.getDataReferenceByName(workspaceId, referenceType, name);
    assertThat(ref.getReferenceId(), equalTo(referenceId));
  }

  @Test
  public void verifyGetDataReference() {
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
    DataReferenceDescription result = dataReferenceDao.getDataReference(workspaceId, referenceId);

    assertThat(result.getWorkspaceId(), equalTo(workspaceId));
    assertThat(result.getReferenceId(), equalTo(referenceId));
    assertThat(result.getName(), equalTo(name));
    assertThat(result.getReferenceType(), equalTo(referenceType));
    //    assertThat(result.getReference().getSnapshot(), equalTo(reference.getSnapshot()));
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

  // TODO: currently no tests enumerating controlled data resources, as we have no way to create
  // them.
}
