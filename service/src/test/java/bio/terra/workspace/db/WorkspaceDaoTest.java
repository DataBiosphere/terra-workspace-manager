package bio.terra.workspace.db;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class WorkspaceDaoTest extends BaseUnitTest {
  private static final String PROJECT_ID = "my-project1";
  private static final String POLICY_OWNER = "policy-owner";
  private static final String POLICY_WRITER = "policy-writer";
  private static final String POLICY_READER = "policy-reader";
  private static final String POLICY_APPLICATION = "policy-application";

  @Autowired private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private ObjectMapper persistenceObjectMapper;

  private UUID workspaceUuid;
  @Nullable private SpendProfileId spendProfileId;
  private static final String READ_SQL =
      "SELECT workspace_id, spend_profile FROM workspace WHERE workspace_id = :id";

  @BeforeEach
  void setup() {
    workspaceUuid = UUID.randomUUID();
    spendProfileId = new SpendProfileId("foo");
  }

  @Test
  void verifyCreatedWorkspaceExists() {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .spendProfileId(spendProfileId)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(workspace);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceUuid.toString()));
    assertThat(queryOutput.get("spend_profile"), equalTo(spendProfileId.getId()));

    // This test doesn't clean up after itself - be sure it only runs on unit test DBs, which
    // are always re-created for tests.
    // TODO: Why does this test not clean up after itself?
  }

  @Test
  void createAndDeleteWorkspace() {
    workspaceDao.createWorkspace(defaultWorkspace());

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertThat(queryOutput.get("workspace_id"), equalTo(workspaceUuid.toString()));

    assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class, () -> jdbcTemplate.queryForMap(READ_SQL, params));
  }

  @Test
  void createAndGetWorkspace() {
    Workspace createdWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(createdWorkspace);

    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);

    assertEquals(createdWorkspace, workspace);

    assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));
  }

  @Test
  void getWorkspacesFromList() {
    Workspace realWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(realWorkspace);
    UUID fakeWorkspaceId = UUID.randomUUID();
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableSet.of(realWorkspace.getWorkspaceId(), fakeWorkspaceId), 0, 1);
    // The DAO should return all workspaces this user has access to, including realWorkspace but
    // not including the fake workspace id.
    assertThat(workspaceList, hasItem(equalTo(realWorkspace)));
    List<UUID> workspaceIdList =
        workspaceList.stream().map(Workspace::getWorkspaceId).collect(Collectors.toList());
    assertThat(workspaceIdList, not(hasItem(equalTo(fakeWorkspaceId))));
  }

  @Test
  void offsetSkipsWorkspaceInList() {
    Workspace firstWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(firstWorkspace);
    UUID uuid = UUID.randomUUID();
    Workspace secondWorkspace =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId(uuid.toString())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(secondWorkspace);
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            1,
            10);
    assertThat(workspaceList.size(), equalTo(1));
    assertThat(workspaceList.get(0), in(ImmutableList.of(firstWorkspace, secondWorkspace)));
  }

  @Test
  void listWorkspaceLimitEnforced() {
    Workspace firstWorkspace = defaultWorkspace();
    workspaceDao.createWorkspace(firstWorkspace);
    UUID uuid = UUID.randomUUID();
    Workspace secondWorkspace =
        Workspace.builder()
            .workspaceId(uuid)
            .userFacingId(uuid.toString())
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .build();
    workspaceDao.createWorkspace(secondWorkspace);
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            0,
            1);
    assertThat(workspaceList.size(), equalTo(1));
    assertThat(workspaceList.get(0), in(ImmutableList.of(firstWorkspace, secondWorkspace)));
  }

  @Test
  void updateWorkspaceProperties() {
    Map<String, String> propertyGenerate =
        new HashMap<String, String>() {
          {
            put("foo", "bar");
            put("xyz", "pqn");
          }
        };

    Workspace initalWorkspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId("a" + workspaceUuid)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .properties(propertyGenerate)
            .build();
    workspaceDao.createWorkspace(initalWorkspace);

    Map<String, String> propertyUpdate = Map.of("foo", "updateBar", "tal", "lass");
    workspaceDao.updateWorkspaceProperties(workspaceUuid, propertyUpdate);
    propertyGenerate.putAll(propertyUpdate);

    assertEquals(propertyGenerate, workspaceDao.getWorkspace(workspaceUuid).getProperties());

    assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));
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
              .userFacingId(mcWorkspaceId.toString())
              .workspaceStage(WorkspaceStage.MC_WORKSPACE)
              .build();
      workspaceDao.createWorkspace(mcWorkspace);
    }

    @Test
    void createAndGetMcWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(mcWorkspaceId);

      assertEquals(mcWorkspace, workspace);
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
    assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceUuid));
  }

  @Test
  void deleteNonExistentWorkspaceFails() {
    assertFalse(workspaceDao.deleteWorkspace(workspaceUuid));
  }

  @Test
  void duplicateWorkspaceFails() {
    Workspace workspace = defaultWorkspace();
    workspaceDao.createWorkspace(workspace);

    assertThrows(DuplicateWorkspaceException.class, () -> workspaceDao.createWorkspace(workspace));
  }

  @Test
  void deleteWorkspaceProperties() {
    Map<String, String> propertyGenerate = Map.of("foo", "bar", "xyz", "pqn");

    Workspace initalWorkspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId("a" + workspaceUuid)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .properties(propertyGenerate)
            .build();
    workspaceDao.createWorkspace(initalWorkspace);

    List<String> propertyUpdate = new ArrayList<>(Arrays.asList("foo", "foo1"));
    workspaceDao.deleteWorkspaceProperties(workspaceUuid, propertyUpdate);

    Map<String, String> updatedProperty = Map.of("xyz", "pqn");

    assertEquals(updatedProperty, workspaceDao.getWorkspace(workspaceUuid).getProperties());

    assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));
  }

  @Nested
  class TestGcpCloudContext {

    @BeforeEach
    void setUp() {
      workspaceDao.createWorkspace(defaultWorkspace());
    }

    @Test
    void createDeleteGcpCloudContext() {
      String flightId = "flight-createdeletegcpcloudcontext";
      gcpCloudContextService.createGcpCloudContextStart(workspaceUuid, flightId);
      gcpCloudContextService.deleteGcpCloudContextWithCheck(workspaceUuid, "mismatched-flight-id");

      GcpCloudContext gcpCloudContext = makeCloudContext();
      gcpCloudContextService.createGcpCloudContextFinish(workspaceUuid, gcpCloudContext, flightId);

      Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
      Optional<GcpCloudContext> cloudContext =
          gcpCloudContextService.getGcpCloudContext(workspace.getWorkspaceId());
      checkCloudContext(cloudContext);

      // Make sure service and dao get the same answer
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      checkCloudContext(cloudContext);

      // delete with mismatched flight id - it should not delete
      gcpCloudContextService.deleteGcpCloudContextWithCheck(workspaceUuid, "mismatched-flight-id");

      // Make sure the context is still there
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      checkCloudContext(cloudContext);

      // delete with no check - should delete
      gcpCloudContextService.deleteGcpCloudContext(workspaceUuid);
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      assertTrue(cloudContext.isEmpty());
    }

    @Test
    void noSetCloudContextIsNone() {
      assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
    }

    @Test
    void deleteWorkspaceWithCloudContext() {
      String flightId = "flight-deleteworkspacewithcloudcontext";
      gcpCloudContextService.createGcpCloudContextStart(workspaceUuid, flightId);
      GcpCloudContext gcpCloudContext = makeCloudContext();
      gcpCloudContextService.createGcpCloudContextFinish(workspaceUuid, gcpCloudContext, flightId);

      assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));
      assertThrows(
          WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceUuid));

      assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
    }
  }

  @Test
  void cloudTypeBackwardsCompatibility() {
    assertEquals(CloudPlatform.GCP, CloudPlatform.valueOf("GCP"));
    assertEquals("GCP", CloudPlatform.GCP.toString());
  }

  private Workspace defaultWorkspace() {
    return Workspace.builder()
        .workspaceId(workspaceUuid)
        .userFacingId(workspaceUuid.toString())
        .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
        .build();
  }

  private GcpCloudContext makeCloudContext() {
    return new GcpCloudContext(
        PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION);
  }

  private void checkCloudContext(Optional<GcpCloudContext> optionalContext) {
    assertTrue(optionalContext.isPresent());
    GcpCloudContext context = optionalContext.get();
    assertEquals(context.getGcpProjectId(), PROJECT_ID);
    assertTrue(context.getSamPolicyOwner().isPresent());
    assertEquals(context.getSamPolicyOwner().get(), POLICY_OWNER);
    assertTrue(context.getSamPolicyWriter().isPresent());
    assertEquals(context.getSamPolicyWriter().get(), POLICY_WRITER);
    assertTrue(context.getSamPolicyReader().isPresent());
    assertEquals(context.getSamPolicyReader().get(), POLICY_READER);
    assertTrue(context.getSamPolicyApplication().isPresent());
    assertEquals(context.getSamPolicyApplication().get(), POLICY_APPLICATION);
  }
}
