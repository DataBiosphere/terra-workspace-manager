package bio.terra.workspace.db;

import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.POLICY_APPLICATION;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.POLICY_OWNER;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.POLICY_READER;
import static bio.terra.workspace.unit.WorkspaceUnitTestUtils.POLICY_WRITER;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import bio.terra.workspace.unit.WorkspaceUnitTestUtils;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class WorkspaceDaoTest extends BaseUnitTest {
  private static final String PROJECT_ID = "my-project1";

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private GcpCloudContextService gcpCloudContextService;

  private UUID workspaceUuid;
  @Nullable private SpendProfileId spendProfileId;
  private static final String READ_SQL =
      "SELECT workspace_id, spend_profile FROM workspace WHERE workspace_id = :id";

  @BeforeEach
  void setup() {
    workspaceUuid = UUID.randomUUID();
    spendProfileId = new SpendProfileId("foo");
  }

  @AfterEach
  void tearDown() {
    workspaceDao.deleteWorkspace(workspaceUuid);
  }

  @Test
  void verifyCreatedWorkspaceExists() {
    workspaceDao.createWorkspace(
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build(), /* applicationIds */
        null);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertEquals(workspaceUuid.toString(), queryOutput.get("workspace_id"));
    assertEquals(spendProfileId.getId(), queryOutput.get("spend_profile"));
  }

  @Test
  void createAndDeleteWorkspace() {
    workspaceDao.createWorkspace(defaultWorkspace(workspaceUuid), /* applicationIds */ null);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertEquals(workspaceUuid.toString(), queryOutput.get("workspace_id"));

    assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class, () -> jdbcTemplate.queryForMap(READ_SQL, params));
  }

  @Test
  void createAndGetWorkspace() {
    Workspace createdWorkspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(createdWorkspace, /* applicationIds */ null);

    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);

    assertEquals(workspace, createdWorkspace);
  }

  @Test
  public void createWorkspace_storeCreatedBy() {
    Workspace createdWorkspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(createdWorkspace, /* applicationIds */ null);

    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);

    assertNotNull(workspace.createdDate());
    assertEquals(DEFAULT_USER_EMAIL, workspace.createdByEmail());
  }

  @Test
  void getWorkspacesFromList() {
    Workspace realWorkspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(realWorkspace, /* applicationIds */ null);
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
  void getWorkspaceToCloudContextMap_getAllGcpCloudContexts() {
    UUID workspace1 = WorkspaceUnitTestUtils.createWorkspaceWithoutGcpContext(workspaceDao);
    UUID workspace2 = WorkspaceUnitTestUtils.createWorkspaceWithoutGcpContext(workspaceDao);
    UUID workspace3 = WorkspaceUnitTestUtils.createWorkspaceWithoutGcpContext(workspaceDao);
    UUID workspace4 = WorkspaceUnitTestUtils.createWorkspaceWithoutGcpContext(workspaceDao);

    String project1 = "gcp-project-1";
    String project2 = "gcp-project-2";
    String project3 = "gcp-project-3";
    WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(workspaceDao, workspace1, project1);
    WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(workspaceDao, workspace2, project2);
    WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(workspaceDao, workspace3, project3);

    Map<UUID, DbCloudContext> workspaceIdToGcpContextMap =
        new HashMap<>(workspaceDao.getWorkspaceIdToGcpCloudContextMap());

    GcpCloudContext context1 =
        GcpCloudContext.deserialize(workspaceIdToGcpContextMap.get(workspace1));
    GcpCloudContext context2 =
        GcpCloudContext.deserialize(workspaceIdToGcpContextMap.get(workspace2));
    GcpCloudContext context3 =
        GcpCloudContext.deserialize(workspaceIdToGcpContextMap.get(workspace3));

    assertContext(project1, context1);
    assertContext(project2, context2);
    assertContext(project3, context3);
  }

  private void assertContext(String projectId, GcpCloudContext cloudContext) {
    assertEquals(projectId, cloudContext.getGcpProjectId());
    assertEquals(CloudPlatform.GCP, cloudContext.getCloudPlatform());
    assertEquals(WsmResourceState.READY, cloudContext.getCommonFields().state());
    assertNull(cloudContext.getCommonFields().flightId());
    assertNull(cloudContext.getCommonFields().error());
  }

  @Test
  void offsetSkipsWorkspaceInList() {
    Workspace firstWorkspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(firstWorkspace, /* applicationIds */ null);
    Workspace secondWorkspace =
        WorkspaceFixtures.buildWorkspace(null, WorkspaceStage.RAWLS_WORKSPACE);
    workspaceDao.createWorkspace(secondWorkspace, /* applicationIds */ null);
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            1,
            10);
    assertEquals(1, workspaceList.size());
    assertThat(workspaceList.get(0), in(ImmutableList.of(firstWorkspace, secondWorkspace)));
  }

  @Test
  void listWorkspaceLimitEnforced() {
    Workspace firstWorkspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(firstWorkspace, /* applicationIds */ null);
    Workspace secondWorkspace =
        WorkspaceFixtures.buildWorkspace(null, WorkspaceStage.RAWLS_WORKSPACE);
    workspaceDao.createWorkspace(secondWorkspace, /* applicationIds */ null);
    List<Workspace> workspaceList =
        workspaceDao.getWorkspacesMatchingList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            0,
            1);
    assertEquals(1, workspaceList.size());
    assertThat(workspaceList.get(0), in(ImmutableList.of(firstWorkspace, secondWorkspace)));
  }

  @Test
  void updateWorkspaceProperties() {
    Map<String, String> propertyGenerate =
        new HashMap<>() {
          {
            put("foo", "bar");
            put("xyz", "pqn");
          }
        };

    Workspace initalWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .properties(propertyGenerate)
            .build();
    workspaceDao.createWorkspace(initalWorkspace, /* applicationIds= */ null);

    Map<String, String> propertyUpdate = Map.of("foo", "updateBar", "tal", "lass");
    workspaceDao.updateWorkspaceProperties(workspaceUuid, propertyUpdate);
    propertyGenerate.putAll(propertyUpdate);

    assertEquals(propertyGenerate, workspaceDao.getWorkspace(workspaceUuid).getProperties());
  }

  @Nested
  class McWorkspace {

    UUID mcWorkspaceId;
    Workspace mcWorkspace;

    @BeforeEach
    void setup() {
      mcWorkspaceId = UUID.randomUUID();
      mcWorkspace =
          WorkspaceFixtures.defaultWorkspaceBuilder(mcWorkspaceId)
              .workspaceStage(WorkspaceStage.MC_WORKSPACE)
              .build();
      workspaceDao.createWorkspace(mcWorkspace, /* applicationIds= */ null);
    }

    @Test
    void createAndGetMcWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(mcWorkspaceId);

      assertEquals(mcWorkspace, workspace);
    }

    @Test
    void getStageMatchesWorkspace() {
      Workspace workspace = workspaceDao.getWorkspace(mcWorkspaceId);
      assertEquals(WorkspaceStage.MC_WORKSPACE, workspace.getWorkspaceStage());
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
    Workspace workspace = defaultWorkspace(workspaceUuid);
    workspaceDao.createWorkspace(workspace, /* applicationIds */ null);

    assertThrows(
        DuplicateWorkspaceException.class,
        () -> workspaceDao.createWorkspace(workspace, /* applicationIds= */ null));
  }

  @Test
  void deleteWorkspaceProperties() {
    Map<String, String> propertyGenerate = Map.of("foo", "bar", "xyz", "pqn");

    Workspace initalWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .properties(propertyGenerate)
            .build();
    workspaceDao.createWorkspace(initalWorkspace, /* applicationIds= */ null);

    List<String> propertyUpdate = new ArrayList<>(Arrays.asList("foo", "foo1"));
    workspaceDao.deleteWorkspaceProperties(workspaceUuid, propertyUpdate);

    Map<String, String> updatedProperty = Map.of("xyz", "pqn");

    assertEquals(updatedProperty, workspaceDao.getWorkspace(workspaceUuid).getProperties());
  }

  @Nested
  class TestGcpCloudContext {

    @BeforeEach
    void setUp() {
      workspaceDao.createWorkspace(defaultWorkspace(workspaceUuid), /* applicationIds */ null);
    }

    @Test
    void createDeleteGcpCloudContext() {
      // Run the normal case
      WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
          workspaceDao, workspaceUuid, PROJECT_ID);
      WorkspaceUnitTestUtils.deleteGcpCloudContextInDatabase(workspaceDao, workspaceUuid);

      // Mismatched flight id
      String flightId = UUID.randomUUID().toString();
      workspaceDao.createCloudContextStart(
          workspaceUuid, CloudPlatform.GCP, WorkspaceUnitTestUtils.SPEND_PROFILE_ID, flightId);

      String gcpContextString = makeCloudContext().serialize();

      // This should fail
      assertThrows(
          ResourceStateConflictException.class,
          () ->
              workspaceDao.createCloudContextSuccess(
                  workspaceUuid, CloudPlatform.GCP, gcpContextString, "mismatched-flight-id"));

      // This should succeed
      workspaceDao.createCloudContextSuccess(
          workspaceUuid, CloudPlatform.GCP, gcpContextString, flightId);

      Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
      Optional<GcpCloudContext> cloudContext =
          gcpCloudContextService.getGcpCloudContext(workspace.getWorkspaceId());
      checkCloudContext(cloudContext);

      // Make sure service and dao get the same answer
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      checkCloudContext(cloudContext);

      // delete with mismatched flight id - it should not delete
      workspaceDao.deleteCloudContextStart(workspaceUuid, CloudPlatform.GCP, flightId);
      assertThrows(
          ResourceStateConflictException.class,
          () ->
              workspaceDao.deleteCloudContextSuccess(
                  workspaceUuid, CloudPlatform.GCP, "mismatched-flight-id"));

      // Make sure the context is still there
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      checkCloudContext(cloudContext);

      // delete with no check - should delete
      workspaceDao.deleteCloudContextSuccess(workspaceUuid, CloudPlatform.GCP, null);

      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      assertTrue(cloudContext.isEmpty());
    }

    @Test
    void noSetCloudContextIsNone() {
      assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
    }

    @Test
    void deleteWorkspaceWithCloudContext() {
      WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
          workspaceDao, workspaceUuid, PROJECT_ID);
      assertTrue(workspaceDao.deleteWorkspace(workspaceUuid));
      assertThrows(
          WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceUuid));

      assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
    }
  }

  private Workspace defaultWorkspace(UUID workspaceUuid) {
    return WorkspaceFixtures.buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);
  }

  private GcpCloudContext makeCloudContext() {
    return new GcpCloudContext(
        PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION, null);
  }

  private void checkCloudContext(Optional<GcpCloudContext> optionalContext) {
    assertTrue(optionalContext.isPresent());
    GcpCloudContext context = optionalContext.get();
    assertEquals(PROJECT_ID, context.getGcpProjectId());
    assertTrue(context.getSamPolicyOwner().isPresent());
    assertEquals(POLICY_OWNER, context.getSamPolicyOwner().get());
    assertTrue(context.getSamPolicyWriter().isPresent());
    assertEquals(POLICY_WRITER, context.getSamPolicyWriter().get());
    assertTrue(context.getSamPolicyReader().isPresent());
    assertEquals(POLICY_READER, context.getSamPolicyReader().get());
    assertTrue(context.getSamPolicyApplication().isPresent());
    assertEquals(POLICY_APPLICATION, context.getSamPolicyApplication().get());
  }
}
