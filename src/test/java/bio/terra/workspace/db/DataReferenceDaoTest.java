package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
  @Autowired ObjectMapper objectMapper;

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
    referenceType = DataReferenceDescription.ReferenceTypeEnum.DATAREPOSNAPSHOT.getValue();

    DataRepoSnapshot drs = new DataRepoSnapshot();
    drs.setInstance(UUID.randomUUID().toString());
    drs.setSnapshot(UUID.randomUUID().toString());
    reference = objectToString(drs);

    credentialId = UUID.randomUUID().toString();
    resourceId =
        null; // eventually we will more thoroughly support controlled resources, so this won't
    // always be null
    cloningInstructions = "COPY_NOTHING";
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void verifyCreatedDataReferenceExists() {
    workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());

    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        name,
        JsonNullable.undefined(),
        JsonNullable.of(credentialId),
        cloningInstructions,
        JsonNullable.of(referenceType),
        JsonNullable.of(reference));
    DataReferenceDescription reference = dataReferenceDao.getDataReference(referenceId);

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
              JsonNullable.undefined(),
              JsonNullable.of(credentialId),
              cloningInstructions,
              JsonNullable.of(referenceType),
              JsonNullable.of(reference.toString()));
        });
  }

  @Test
  public void verifyGetDataReference() {
    workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());

    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        name,
        JsonNullable.undefined(),
        JsonNullable.of(credentialId),
        cloningInstructions,
        JsonNullable.of(referenceType),
        JsonNullable.of(reference.toString()));
    DataReferenceDescription result = dataReferenceDao.getDataReference(referenceId);

    assertThat(result.getWorkspaceId(), equalTo(workspaceId));
    assertThat(result.getReferenceId(), equalTo(referenceId));
    assertThat(result.getName(), equalTo(name));
    assertThat(
        result.getReferenceType(),
        equalTo(
            JsonNullable.of(DataReferenceDescription.ReferenceTypeEnum.fromValue(referenceType))));
    //    assertThat(result.getReference().getSnapshot(), equalTo(reference.getSnapshot()));
    //    assertThat(result.getReference().getInstance(), equalTo(reference.getInstance()));
  }

  @Test
  public void verifyDeleteDataReference() {
    workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());

    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        name,
        JsonNullable.undefined(),
        JsonNullable.of(credentialId),
        cloningInstructions,
        JsonNullable.of(referenceType),
        JsonNullable.of(reference.toString()));

    assertTrue(dataReferenceDao.deleteDataReference(referenceId));

    // try to delete again to make sure it's not there
    assertFalse(dataReferenceDao.deleteDataReference(referenceId));
  }

  @Test
  public void deleteNonExistentWorkspaceFails() throws Exception {
    assertFalse(dataReferenceDao.deleteDataReference(referenceId));
  }

  private String objectToString(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new InvalidDataReferenceException("Invalid data reference");
    }
  }
}
