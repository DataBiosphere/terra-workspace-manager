package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class DataReferenceDaoTest {

  @Autowired WorkspaceManagerJdbcConfiguration jdbcConfiguration;

  private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired DataReferenceDao dataReferenceDao;
  @Autowired WorkspaceDao workspaceDao;

  private UUID workspaceId;
  private UUID referenceId;
  private String name;
  private String referenceType;
  private String reference;
  private String credentialId;
  private UUID resourceId;
  private String cloningInstructions;

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    referenceId = UUID.randomUUID();
    name = UUID.randomUUID().toString();
    referenceType = UUID.randomUUID().toString();
    reference = "{\"fake_key\": \"fake_value\"}";
    credentialId = UUID.randomUUID().toString();
    resourceId =
        null; // eventually we will more thoroughly support controlled resources, so this won't
    // always be null
    cloningInstructions = "COPY_NOTHING";
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  // TODO: This should be uncommented and tweaked when a final version of the Create endpoint is
  // available.
  // The tests below all passed using an in-progress version of the Create endpoint.

  // @Test
  // public void verifyCreatedDataReferenceExists() {
  //   workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //
  //   dataReferenceDao.createDataReference(
  //       referenceId,
  //       workspaceId,
  //       name,
  //       JsonNullable.undefined(),
  //       JsonNullable.of(credentialId),
  //       cloningInstructions,
  //       JsonNullable.of(referenceType),
  //       JsonNullable.of(reference));
  //   DataReference reference = dataReferenceDao.getDataReference(referenceId);
  //
  //   assertThat(reference.getReferenceId(), equalTo(referenceId));
  // }
  //
  // @Test
  // public void createReferenceWithoutWorkspaceFails() throws Exception {
  //   assertThrows(
  //       DataIntegrityViolationException.class,
  //       () -> {
  //         dataReferenceDao.createDataReference(
  //             referenceId,
  //             UUID.randomUUID(), // non-existing workspace ID
  //             name,
  //             JsonNullable.undefined(),
  //             JsonNullable.of(credentialId),
  //             cloningInstructions,
  //             JsonNullable.of(referenceType),
  //             JsonNullable.of(reference));
  //       });
  // }
  //
  // @Test
  // public void verifyGetDataReference() {
  //   workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //
  //   dataReferenceDao.createDataReference(
  //       referenceId,
  //       workspaceId,
  //       name,
  //       JsonNullable.undefined(),
  //       JsonNullable.of(credentialId),
  //       cloningInstructions,
  //       JsonNullable.of(referenceType),
  //       JsonNullable.of(reference));
  //   DataReference result = dataReferenceDao.getDataReference(referenceId);
  //
  //   assertThat(result.getWorkspaceId(), equalTo(workspaceId));
  //   assertThat(result.getReferenceId(), equalTo(referenceId));
  //   assertThat(result.getName(), equalTo(name));
  //   assertThat(result.getReferenceType(), equalTo(JsonNullable.of(referenceType)));
  //   assertThat(result.getReference(), equalTo(JsonNullable.of(reference)));
  // }
  //
  // @Test
  // public void verifyDeleteDataReference() {
  //   workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //
  //   dataReferenceDao.createDataReference(
  //       referenceId,
  //       workspaceId,
  //       name,
  //       JsonNullable.undefined(),
  //       JsonNullable.of(credentialId),
  //       cloningInstructions,
  //       JsonNullable.of(referenceType),
  //       JsonNullable.of(reference));
  //
  //   assertTrue(dataReferenceDao.deleteDataReference(referenceId));
  //
  //   // try to delete again to make sure it's not there
  //   assertFalse(dataReferenceDao.deleteDataReference(referenceId));
  // }
  //
  // @Test
  // public void deleteNonExistentWorkspaceFails() throws Exception {
  //   assertFalse(dataReferenceDao.deleteDataReference(referenceId));
  // }
  //
  // @Test
  // public void enumerateWorkspaceReferences() throws Exception {
  //   workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //   // Create two references in the same workspace.
  //   dataReferenceDao.createDataReference(
  //       referenceId,
  //       workspaceId,
  //       name,
  //       JsonNullable.undefined(),
  //       JsonNullable.of(credentialId),
  //       cloningInstructions,
  //       JsonNullable.of(referenceType),
  //       JsonNullable.of(reference));
  //   DataReference firstReference = dataReferenceDao.getDataReference(referenceId);
  //
  //   UUID secondReferenceId = UUID.randomUUID();
  //   dataReferenceDao.createDataReference(
  //       secondReferenceId,
  //       workspaceId,
  //       name,
  //       JsonNullable.undefined(),
  //       JsonNullable.of(credentialId),
  //       cloningInstructions,
  //       JsonNullable.of(referenceType),
  //       JsonNullable.of(reference));
  //   DataReference secondReference = dataReferenceDao.getDataReference(secondReferenceId);
  //
  //   // Validate that both DataReferences are enumerated
  //   DataReferenceList enumerateResult =
  //       dataReferenceDao.enumerateDataReferences(workspaceId.toString(), name, 0, 10, "all");
  //   assertThat(enumerateResult.getResources().size(), equalTo(2));
  //   assertThat(
  //       enumerateResult.getResources(),
  //       containsInAnyOrder(equalTo(firstReference), equalTo(secondReference)));
  // }
  //
  // @Test
  // public void enumerateEmptyReferenceList() throws Exception {
  //   workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //
  //   DataReferenceList result =
  //       dataReferenceDao.enumerateDataReferences(workspaceId.toString(), name, 0, 10, "all");
  //   assertThat(result.getResources(), empty());
  // }

  // TODO: currently no tests about controlled data resources, as we have no way to create them.
}
