package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.generated.model.WorkspaceStageModel;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class WorkspaceDaoTest extends BaseUnitTest {

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private WorkspaceDao workspaceDao;

  private UUID workspaceId;
  private UUID spendProfileId;
  private String readSql =
      "SELECT workspace_id, spend_profile, profile_settable FROM workspace WHERE workspace_id = :id";

  @BeforeEach
  public void setup() {
    workspaceId = UUID.randomUUID();
    spendProfileId = UUID.randomUUID();
  }

  @Test
  public void verifyCreatedWorkspaceExists() throws Exception {
    workspaceDao.createWorkspace(workspaceId, spendProfileId, WorkspaceStage.RAWLS_WORKSPACE);
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("spend_profile"), equalTo(spendProfileId.toString()));
    assertThat(queryOutput.get("profile_settable"), equalTo(false));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
  }

  @Test
  public void createAndDeleteWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("profile_settable"), equalTo(true));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class,
        () -> {
          jdbcTemplate.queryForMap(readSql, params);
        });
  }

  @Test
  public void createAndGetWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);

    WorkspaceDescription workspace = workspaceDao.getWorkspace(workspaceId);

    WorkspaceDescription expectedWorkspace = new WorkspaceDescription();
    expectedWorkspace.setId(workspaceId);
    expectedWorkspace.setSpendProfile(null);
    expectedWorkspace.setStage(WorkspaceStageModel.RAWLS_WORKSPACE);

    assertThat(workspace, equalTo(expectedWorkspace));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));
  }

  @Test
  public void createAndGetMcWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.MC_WORKSPACE);

    WorkspaceDescription workspace = workspaceDao.getWorkspace(workspaceId);

    WorkspaceDescription expectedWorkspace = new WorkspaceDescription();
    expectedWorkspace.setId(workspaceId);
    expectedWorkspace.setSpendProfile(null);
    expectedWorkspace.setStage(WorkspaceStageModel.MC_WORKSPACE);

    assertThat(workspace, equalTo(expectedWorkspace));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));
  }

  @Test
  public void getStageMatchesWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.MC_WORKSPACE);
    WorkspaceDescription workspace = workspaceDao.getWorkspace(workspaceId);
    WorkspaceStage stage = workspaceDao.getWorkspaceStage(workspaceId);
    assertThat(stage, equalTo(WorkspaceStage.MC_WORKSPACE));
    assertThat(stage, equalTo(WorkspaceStage.fromApiModel(workspace.getStage())));
  }

  @Test
  public void getNonExistingWorkspace() throws Exception {

    assertThrows(
        WorkspaceNotFoundException.class,
        () -> {
          workspaceDao.getWorkspace(workspaceId);
        });
  }

  @Test
  public void deleteNonExistentWorkspaceFails() throws Exception {
    assertFalse(workspaceDao.deleteWorkspace(workspaceId));
  }

  @Test
  public void duplicateWorkspaceFails() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> {
          workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);
        });
  }

  @Test
  public void updateCloudContext_Google() {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);

    WorkspaceCloudContext googleContext1 = WorkspaceCloudContext.createGoogleContext("my-project1");
    workspaceDao.updateCloudContext(workspaceId, googleContext1);
    assertEquals(googleContext1, workspaceDao.getCloudContext(workspaceId));

    WorkspaceCloudContext googleContext2 = WorkspaceCloudContext.createGoogleContext("my-project2");
    workspaceDao.updateCloudContext(workspaceId, googleContext2);
    assertEquals(googleContext2, workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void updateCloudContext_None() {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);

    WorkspaceCloudContext noneContext = WorkspaceCloudContext.none();
    workspaceDao.updateCloudContext(workspaceId, noneContext);
    assertEquals(noneContext, workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void noSetCloudContextIsNone() {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void updateAndNoneCloudContext() {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);

    workspaceDao.updateCloudContext(
        workspaceId, WorkspaceCloudContext.createGoogleContext("my-project"));
    workspaceDao.updateCloudContext(workspaceId, WorkspaceCloudContext.none());
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void deleteWorkspaceWithCloudContext() {
    workspaceDao.createWorkspace(workspaceId, null, WorkspaceStage.RAWLS_WORKSPACE);
    workspaceDao.updateCloudContext(
        workspaceId, WorkspaceCloudContext.createGoogleContext("my-project"));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));
    assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceId));
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  public void googleCloudContextBackwardsCompatibility() throws Exception {
    WorkspaceDao.GoogleCloudContextV1 googleDeserialized =
        WorkspaceDao.GoogleCloudContextV1.deserialize(
            "{\"version\":1,\"googleProjectId\":\"foo\"}");
    assertEquals(1, googleDeserialized.version);
    assertEquals("foo", googleDeserialized.googleProjectId);
  }

  @Test
  public void cloudTypeBackwardsCompatibility() {
    assertEquals(WorkspaceDao.CloudType.GOOGLE, WorkspaceDao.CloudType.valueOf("GOOGLE"));
    assertEquals("GOOGLE", WorkspaceDao.CloudType.GOOGLE.toString());
    assertEquals(1, WorkspaceDao.CloudType.values().length);
  }
}
