package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.generated.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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
    workspaceDao.createWorkspace(workspaceId, spendProfileId);
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, paramMap);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("spend_profile"), equalTo(spendProfileId.toString()));
    assertThat(queryOutput.get("profile_settable"), equalTo(false));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
  }

  @Test
  public void createAndDeleteWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null);
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(readSql, paramMap);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("profile_settable"), equalTo(true));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class,
        () -> {
          jdbcTemplate.queryForMap(readSql, paramMap);
        });
  }

  @Test
  public void createAndGetWorkspace() throws Exception {
    workspaceDao.createWorkspace(workspaceId, null);

    WorkspaceDescription workspace = workspaceDao.getWorkspace(workspaceId);

    WorkspaceDescription expectedWorkspace = new WorkspaceDescription();
    expectedWorkspace.setId(workspaceId);
    expectedWorkspace.setSpendProfile(null);

    assertThat(workspace, equalTo(expectedWorkspace));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));
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
    workspaceDao.createWorkspace(workspaceId, null);
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> {
          workspaceDao.createWorkspace(workspaceId, null);
        });
  }

  @Test
  public void insertAndGetCloudContext_Google() {
    workspaceDao.createWorkspace(workspaceId, null);
    WorkspaceCloudContext googleContext = WorkspaceCloudContext.createGoogleContext("my-project");
    workspaceDao.insertCloudContext(workspaceId, googleContext);

    assertEquals(googleContext, workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void insertAndGetCloudContext_None() {
    workspaceDao.createWorkspace(workspaceId, null);
    WorkspaceCloudContext noneContext = WorkspaceCloudContext.none();
    workspaceDao.insertCloudContext(workspaceId, noneContext);

    assertEquals(noneContext, workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void insertAndDeleteCloudContext() {
    workspaceDao.createWorkspace(workspaceId, null);
    assertFalse(workspaceDao.deleteCloudContext(workspaceId));
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));

    workspaceDao.insertCloudContext(
        workspaceId, WorkspaceCloudContext.createGoogleContext("my-project"));
    assertTrue(workspaceDao.deleteCloudContext(workspaceId));
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  @Test
  public void noSetCloudContextIsNone() {
    workspaceDao.createWorkspace(workspaceId, null);
    assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
  }

  /**
   * Hard code serialized values to check that code changes do not break backwards compatibility of
   * stored JSON values. If this test fails, your change may not work with existing databases.
   */
  @Test
  public void cloudContextBackwardsCompatibility() throws Exception {
    WorkspaceDao.CloudContextV1 googleDeserialized =
        WorkspaceDao.CloudContextV1.deserialize("{\"version\":1,\"googleProjectId\":\"foo\"}");
    assertEquals(1, googleDeserialized.version);
    assertEquals("foo", googleDeserialized.googleProjectId);

    WorkspaceDao.CloudContextV1 noneDeserialized =
        WorkspaceDao.CloudContextV1.deserialize("{\"version\":1,\"googleProjectId\":null}");
    assertEquals(1, noneDeserialized.version);
    assertNull(noneDeserialized.googleProjectId);
  }
}
