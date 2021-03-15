package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class WorkspaceDaoTest extends BaseUnitTest {

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ObjectMapper persistenceObjectMapper;

  private UUID workspaceId;
  @Nullable private SpendProfileId spendProfileId;
  private final String READ_SQL =
      "SELECT workspace_id, spend_profile FROM workspace WHERE workspace_id = :id";

  @BeforeEach
  void setup() {
    workspaceId = UUID.randomUUID();
    spendProfileId = SpendProfileId.create("foo");
  }

  @Test
  void verifyCreatedWorkspaceExists() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceId)
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("spend_profile"), equalTo(spendProfileId.id()));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
    // TODO: Why does this test not clean up after itself?
  }

  @Test
  void createAndDeleteWorkspace() {
    workspaceDao.createWorkspace(defaultWorkspace());

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class, () -> jdbcTemplate.queryForMap(READ_SQL, params));
  }

  @Test
  void createAndGetWorkspace() {
    Workspace createdWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(createdWorkspace);

    Workspace workspace = workspaceDao.getWorkspace(workspaceId);

    assertEquals(workspace, createdWorkspace);

    assertTrue(workspaceDao.deleteWorkspace(workspaceId));
  }

  @Nested
  class McWorkspace {

    UUID mcWorkspaceId;
    Workspace mcWorkspace;

    @BeforeEach
    void setup() {
      mcWorkspaceId = UUID.randomUUID();
      mcWorkspace =
          Workspace.builder()
              .workspaceId(mcWorkspaceId)
              .workspaceStage(WorkspaceStage.MC_WORKSPACE)
              .build();
      workspaceDao.createWorkspace(mcWorkspace);
    }

    @Test
    void createAndGetMcWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(mcWorkspaceId);

      assertEquals(workspace, mcWorkspace);
      assertTrue(workspaceDao.deleteWorkspace(mcWorkspaceId));
    }

    @Test
    void getStageMatchesWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(mcWorkspaceId);
      assertThat(workspace.getWorkspaceStage(), equalTo(WorkspaceStage.MC_WORKSPACE));
    }
  }

  @Test
  void getNonExistingWorkspace() {
    assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceId));
  }

  @Test
  void deleteNonExistentWorkspaceFails() {
    assertFalse(workspaceDao.deleteWorkspace(workspaceId));
  }

  @Test
  void duplicateWorkspaceFails() {
    Workspace workspace = defaultWorkspace();
    workspaceDao.createWorkspace(workspace);

    assertThrows(DuplicateWorkspaceException.class, () -> workspaceDao.createWorkspace(workspace));
  }

  @Nested
  class TestGcpCloudContext {

    @BeforeEach
    void setUp() {
      workspaceDao.createWorkspace(defaultWorkspace());
    }

    @Test
    void createDeleteGcpCloudContext() {
      String projectId = "my-project1";
      GcpCloudContext gcpCloudContext = new GcpCloudContext(projectId);
      workspaceDao.createGcpCloudContext(workspaceId, gcpCloudContext);

      Workspace workspace = workspaceDao.getWorkspace(workspaceId);
      assertTrue(workspace.getGcpCloudContext().isPresent());
      assertEquals(projectId, workspace.getGcpCloudContext().get().getGcpProjectId());

      Optional<GcpCloudContext> dbGcpCloudContext = workspaceDao.getGcpCloudContext(workspaceId);
      assertTrue(dbGcpCloudContext.isPresent());
      assertEquals(projectId, dbGcpCloudContext.get().getGcpProjectId());

      workspaceDao.deleteGcpCloudContext(workspaceId);
      workspace = workspaceDao.getWorkspace(workspaceId);
      assertTrue(workspace.getGcpCloudContext().isEmpty());

      dbGcpCloudContext = workspaceDao.getGcpCloudContext(workspaceId);
      assertTrue(dbGcpCloudContext.isEmpty());
    }

    @Test
    void noSetCloudContextIsNone() {
      Workspace workspace = workspaceDao.getWorkspace(workspaceId);
      assertTrue(workspace.getGcpCloudContext().isEmpty());
    }

    @Test
    void deleteWorkspaceWithCloudContext() {
      String projectId = "my-project1";
      GcpCloudContext gcpCloudContext = new GcpCloudContext(projectId);
      workspaceDao.createGcpCloudContext(workspaceId, gcpCloudContext);

      assertTrue(workspaceDao.deleteWorkspace(workspaceId));
      assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceId));

      assertTrue(workspaceDao.getGcpCloudContext(workspaceId).isEmpty());
    }

    /**
     * Hard code serialized values to check that code changes do not break backwards compatibility
     * of stored JSON values. If this test fails, your change may not work with existing databases.
     */
    @Test
    void gcpCloudContextBackwardsCompatibility() throws Exception {
      final String json = "{\"version\":1,\"gcpProjectId\":\"foo\"}";
      WorkspaceDao.GcpCloudContextV1 gcpCloudContextV1 =
          persistenceObjectMapper.readValue(json, WorkspaceDao.GcpCloudContextV1.class);
      assertEquals(WorkspaceDao.GCP_CLOUD_CONTEXT_DB_VERSION, gcpCloudContextV1.version);
      assertEquals("foo", gcpCloudContextV1.gcpProjectId);

      GcpCloudContext gcpCloudContext = workspaceDao.deserializeGcpCloudContext(json);
      assertEquals("foo", gcpCloudContext.getGcpProjectId());
    }
  }

  @Test
  void cloudTypeBackwardsCompatibility() {
    assertEquals(CloudPlatform.GCP, CloudPlatform.valueOf("GCP"));
    assertEquals("GCP", CloudPlatform.GCP.toString());
  }

  private Workspace defaultWorkspace() {
    return Workspace.builder()
        .workspaceId(workspaceId)
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
        .build();
  }
}
