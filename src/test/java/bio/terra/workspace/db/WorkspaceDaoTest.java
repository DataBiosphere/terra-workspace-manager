package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.exception.DuplicateWorkspaceException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private UUID workspaceId;
  private Optional<SpendProfileId> spendProfileId;
  private static final String READ_SQL =
      "SELECT workspace_id, spend_profile, profile_settable FROM workspace WHERE workspace_id = :id";

  @BeforeEach
  void setup() {
    workspaceId = UUID.randomUUID();
    spendProfileId = Optional.of(SpendProfileId.create("foo"));
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
    assertThat(queryOutput.get("spend_profile"), equalTo(spendProfileId.get().id()));
    assertThat(queryOutput.get("profile_settable"), equalTo(false));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
  }

  @Test
  void createAndDeleteWorkspace() {
    workspaceDao.createWorkspace(defaultWorkspace());

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceId.toString()));
    assertThat(queryOutput.get("profile_settable"), equalTo(true));

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

  @Test
  void getWorkspacesFromList() {
    Workspace realWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(realWorkspace);
    UUID fakeWorkspaceId = UUID.randomUUID();
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableList.of(realWorkspace.workspaceId(), fakeWorkspaceId));
    // The DAO should return all workspaces this user has access to, including realWorkspace but
    // not including the fake workspace id.
    assertThat(workspaceList, hasItem(equalTo(realWorkspace)));
    List<UUID> workspaceIdList =
        workspaceList.stream().map(Workspace::workspaceId).collect(Collectors.toList());
    assertThat(workspaceIdList, not(hasItem(equalTo(fakeWorkspaceId))));
  }

  @Nested
  class McWorkspace {

    Workspace mcWorkspace;

    @BeforeEach
    void setup() {
      mcWorkspace =
          Workspace.builder()
              .workspaceId(workspaceId)
              .workspaceStage(WorkspaceStage.MC_WORKSPACE)
              .build();
      workspaceDao.createWorkspace(mcWorkspace);
    }

    @Test
    void createAndGetMcWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(workspaceId);

      assertEquals(workspace, mcWorkspace);
      assertTrue(workspaceDao.deleteWorkspace(workspaceId));
    }

    @Test
    void getStageMatchesWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(workspaceId);
      WorkspaceStage stage = workspaceDao.getWorkspaceStage(workspaceId);

      assertThat(stage, equalTo(WorkspaceStage.MC_WORKSPACE));
      assertThat(stage, equalTo(workspace.workspaceStage()));
    }
  }

  @Test
  void getStageNonExistingWorkspaceFails() {
    assertThrows(
        WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspaceStage(workspaceId));
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
  class CloudContext {

    @BeforeEach
    void setUp() {
      workspaceDao.createWorkspace(defaultWorkspace());
    }

    @Test
    void updateCloudContext_Google() {
      WorkspaceCloudContext googleContext1 =
          WorkspaceCloudContext.builder().googleProjectId("my-project1").build();
      workspaceDao.updateCloudContext(workspaceId, googleContext1);
      assertEquals(googleContext1, workspaceDao.getCloudContext(workspaceId));

      WorkspaceCloudContext googleContext2 =
          WorkspaceCloudContext.builder().googleProjectId("my-project2").build();
      workspaceDao.updateCloudContext(workspaceId, googleContext2);
      assertEquals(googleContext2, workspaceDao.getCloudContext(workspaceId));
    }

    @Test
    void updateCloudContext_None() {
      WorkspaceCloudContext noneContext = WorkspaceCloudContext.none();
      workspaceDao.updateCloudContext(workspaceId, noneContext);
      assertEquals(noneContext, workspaceDao.getCloudContext(workspaceId));
    }

    @Test
    void noSetCloudContextIsNone() {
      assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    }

    @Test
    void updateAndNoneCloudContext() {
      workspaceDao.updateCloudContext(
          workspaceId, WorkspaceCloudContext.builder().googleProjectId(("my-project")).build());
      workspaceDao.updateCloudContext(workspaceId, WorkspaceCloudContext.none());
      assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    }

    @Test
    void deleteWorkspaceWithCloudContext() {
      workspaceDao.updateCloudContext(
          workspaceId, WorkspaceCloudContext.builder().googleProjectId(("my-project")).build());

      assertTrue(workspaceDao.deleteWorkspace(workspaceId));
      assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceId));
      assertEquals(WorkspaceCloudContext.none(), workspaceDao.getCloudContext(workspaceId));
    }

    /**
     * Hard code serialized values to check that code changes do not break backwards compatibility
     * of stored JSON values. If this test fails, your change may not work with existing databases.
     */
    @Test
    void googleCloudContextBackwardsCompatibility() throws Exception {
      WorkspaceDao.GoogleCloudContextV1 googleDeserialized =
          WorkspaceDao.GoogleCloudContextV1.deserialize(
              "{\"version\":1,\"googleProjectId\":\"foo\"}");
      assertEquals(1, googleDeserialized.version);
      assertEquals("foo", googleDeserialized.googleProjectId);
    }
  }

  @Test
  void cloudTypeBackwardsCompatibility() {
    assertEquals(WorkspaceDao.CloudType.GOOGLE, WorkspaceDao.CloudType.valueOf("GOOGLE"));
    assertEquals("GOOGLE", WorkspaceDao.CloudType.GOOGLE.toString());
    assertEquals(1, WorkspaceDao.CloudType.values().length);
  }

  private Workspace defaultWorkspace() {
    return Workspace.builder()
        .workspaceId(workspaceId)
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
        .build();
  }
}
