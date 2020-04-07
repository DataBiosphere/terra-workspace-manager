package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.model.DataRepoSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
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
  private UUID spendProfileId;
  private UUID referenceId;
  private String readSql =
      "SELECT workspace_id, reference_id, name, reference_type, reference FROM workspace_data_reference WHERE reference_id = :id";

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    spendProfileId = UUID.randomUUID();
    referenceId = UUID.randomUUID();
    jdbcTemplate = new NamedParameterJdbcTemplate(jdbcConfiguration.getDataSource());
  }

  @Test
  public void verifyCreatedDataReferenceExists() throws Exception {
    workspaceDao.createWorkspace(workspaceId, JsonNullable.of(spendProfileId));

    dataReferenceDao.createDataReference(
        referenceId, workspaceId, "testName", "tdr-snapshot", new DataRepoSnapshot("foo", "bar"));
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", referenceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, paramMap);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("reference_id"), equalTo(referenceId.toString()));
    assertThat(queryOutput.get("name"), equalTo("testName"));
    assertThat(queryOutput.get("reference_type"), equalTo("tdr-snapshot"));
    assertThat(queryOutput.get("reference"), equalTo(new DataRepoSnapshot("foo", "bar")));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
  }

  //    @Test
  //    public void createAndDeleteWorkspace() throws Exception {
  //        workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //        Map<String, Object> paramMap = new HashMap<>();
  //        paramMap.put("id", workspaceId.toString());
  //        Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, paramMap);
  //
  //        assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
  //        assertThat(queryOutput.get("profile_settable"), equalTo(true));
  //
  //        assertTrue(workspaceDao.deleteWorkspace(workspaceId));
  //
  //        // Assert the object no longer exists after deletion
  //        assertThrows(
  //                EmptyResultDataAccessException.class,
  //                () -> {
  //                    jdbcTemplate.queryForMap(readSql, paramMap);
  //                });
  //    }
  //
  //    @Test
  //    public void createAndGetWorkspace() throws Exception {
  //        workspaceDao.createWorkspace(workspaceId, JsonNullable.undefined());
  //
  //        WorkspaceDescription workspace = workspaceDao.getWorkspace(workspaceId.toString());
  //
  //        WorkspaceDescription expectedWorkspace = new WorkspaceDescription();
  //        expectedWorkspace.setId(workspaceId);
  //        expectedWorkspace.setSpendProfile(JsonNullable.undefined());
  //
  //        assertThat(workspace, equalTo(expectedWorkspace));
  //
  //        assertTrue(workspaceDao.deleteWorkspace(workspaceId));
  //    }
  //
  //    @Test
  //    public void getNonExistingWorkspace() throws Exception {
  //
  //        assertThrows(
  //                WorkspaceNotFoundException.class,
  //                () -> {
  //                    workspaceDao.getWorkspace(workspaceId.toString());
  //                });
  //    }
  //
  //    @Test
  //    public void deleteNonExistentWorkspaceFails() throws Exception {
  //        assertFalse(workspaceDao.deleteWorkspace(workspaceId));
  //    }
}
