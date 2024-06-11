package bio.terra.workspace.db;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.POLICY_APPLICATION;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.POLICY_OWNER;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.POLICY_READER;
import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.POLICY_WRITER;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.exception.FieldSizeExceededException;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.db.model.DbWorkspace;
import bio.terra.workspace.db.model.DbWorkspaceDescription;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.GcpCloudContextFields;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class WorkspaceDaoTest extends BaseSpringBootUnitTest {
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
    try {
      WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao);
    } catch (WorkspaceNotFoundException e) {
      // this is just fine for these tests
    }
  }

  @Test
  void verifyCreatedWorkspaceExists() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);

    assertEquals(workspaceUuid.toString(), queryOutput.get("workspace_id"));
    assertEquals(spendProfileId.getId(), queryOutput.get("spend_profile"));

    Workspace gotWorkspace = workspaceDao.getWorkspace(workspaceUuid);
    assertEquals(WorkspaceStage.MC_WORKSPACE, gotWorkspace.workspaceStage());
  }

  @Test
  void createAndDeleteWorkspace() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceUuid.toString());
    Map<String, Object> queryOutput = jdbcTemplate.queryForMap(READ_SQL, params);
    assertEquals(workspaceUuid.toString(), queryOutput.get("workspace_id"));
    assertTrue(WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao));

    // Assert the object no longer exists after deletion
    assertThrows(
        EmptyResultDataAccessException.class, () -> jdbcTemplate.queryForMap(READ_SQL, params));
  }

  @Test
  void createAndGetRawlsWorkspace() {
    Workspace createdWorkspace = defaultRawlsWorkspace(workspaceUuid);
    WorkspaceFixtures.createWorkspaceInDb(createdWorkspace, workspaceDao);
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);

    assertEquals(workspace.workspaceId(), createdWorkspace.workspaceId());
    assertEquals(workspace.userFacingId(), createdWorkspace.userFacingId());
    assertEquals(workspace.spendProfileId(), createdWorkspace.spendProfileId());
    assertEquals(workspace.workspaceStage(), createdWorkspace.workspaceStage());
    assertEquals(WorkspaceStage.RAWLS_WORKSPACE, workspace.workspaceStage());
    assertEquals(WsmResourceState.READY, workspace.state());
    assertNotNull(workspace.createdDate());
    assertEquals(DEFAULT_USER_EMAIL, workspace.createdByEmail());
  }

  @Test
  void getWorkspacesFromList() {
    Workspace realWorkspace = defaultRawlsWorkspace(workspaceUuid);
    WorkspaceFixtures.createWorkspaceInDb(realWorkspace, workspaceDao);
    UUID fakeWorkspaceId = UUID.randomUUID();
    Map<UUID, DbWorkspaceDescription> workspaceList =
        workspaceDao.getWorkspaceDescriptionMapFromIdList(
            ImmutableSet.of(realWorkspace.getWorkspaceId(), fakeWorkspaceId), 0, 1);
    // The DAO should return all workspaces this user has access to, including realWorkspace but
    // not including the fake workspace id.
    DbWorkspaceDescription realWorkspaceDescription =
        workspaceList.get(realWorkspace.getWorkspaceId());
    assertEquals(realWorkspaceDescription.getWorkspace(), realWorkspace);
    assertThat(workspaceList.keySet(), not(hasItem(equalTo(fakeWorkspaceId))));
  }

  @Test
  void getWorkspaceToCloudContextMap_getAllGcpCloudContexts() {
    UUID workspace1 = WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext(workspaceDao);
    UUID workspace2 = WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext(workspaceDao);
    UUID workspace3 = WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext(workspaceDao);

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
    Workspace firstWorkspace = defaultRawlsWorkspace(workspaceUuid);
    WorkspaceFixtures.createWorkspaceInDb(firstWorkspace, workspaceDao);
    Workspace secondWorkspace =
        WorkspaceFixtures.buildWorkspace(null, WorkspaceStage.RAWLS_WORKSPACE);
    WorkspaceFixtures.createWorkspaceInDb(secondWorkspace, workspaceDao);
    Map<UUID, DbWorkspaceDescription> workspaceMap =
        workspaceDao.getWorkspaceDescriptionMapFromIdList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            1,
            10);
    assertEquals(1, workspaceMap.size());
    assertTrue(
        workspaceMap.containsKey(firstWorkspace.getWorkspaceId())
            || workspaceMap.containsKey(secondWorkspace.getWorkspaceId()));
  }

  @Test
  void listWorkspaceLimitEnforced() {
    Workspace firstWorkspace = defaultRawlsWorkspace(workspaceUuid);
    WorkspaceFixtures.createWorkspaceInDb(firstWorkspace, workspaceDao);
    Workspace secondWorkspace =
        WorkspaceFixtures.buildWorkspace(null, WorkspaceStage.RAWLS_WORKSPACE);
    WorkspaceFixtures.createWorkspaceInDb(secondWorkspace, workspaceDao);
    Map<UUID, DbWorkspaceDescription> workspaceMap =
        workspaceDao.getWorkspaceDescriptionMapFromIdList(
            ImmutableSet.of(firstWorkspace.getWorkspaceId(), secondWorkspace.getWorkspaceId()),
            0,
            1);
    assertEquals(1, workspaceMap.size());
    assertTrue(
        workspaceMap.containsKey(firstWorkspace.getWorkspaceId())
            || workspaceMap.containsKey(secondWorkspace.getWorkspaceId()));
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
    WorkspaceFixtures.createWorkspaceInDb(initalWorkspace, workspaceDao);
    Map<String, String> propertyUpdate = Map.of("foo", "updateBar", "tal", "lass");
    workspaceDao.updateWorkspaceProperties(workspaceUuid, propertyUpdate);
    propertyGenerate.putAll(propertyUpdate);

    assertEquals(propertyGenerate, workspaceDao.getWorkspace(workspaceUuid).getProperties());
  }

  @Test
  void getNonExistingWorkspace() {
    assertThrows(WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceUuid));
  }

  @Test
  void deleteNonExistentWorkspaceFails() {
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> workspaceDao.deleteWorkspaceStart(workspaceUuid, UUID.randomUUID().toString()));
  }

  @Test
  void duplicateWorkspaceFails() {
    Workspace workspace = defaultRawlsWorkspace(workspaceUuid);
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    assertThrows(
        DuplicateWorkspaceException.class,
        () -> WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao));
  }

  @Test
  void duplicateWorkspaceCreateFailureDoesNotDelete() {
    Workspace workspace = defaultRawlsWorkspace(workspaceUuid);
    // Create the workspace normally
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    // A later call to createWorkspaceFailure should only delete if the caller is the same flight
    // which created the workspace.
    workspaceDao.createWorkspaceFailure(
        workspaceUuid,
        /* flightId= */ UUID.randomUUID().toString(),
        /* exception= */ null,
        WsmResourceStateRule.DELETE_ON_FAILURE);
    Workspace workspaceAfterDelete = workspaceDao.getWorkspace(workspaceUuid);
    assertEquals(workspace, workspaceAfterDelete);
  }

  @Test
  void deleteWorkspaceProperties() {
    Map<String, String> propertyGenerate = Map.of("foo", "bar", "xyz", "pqn");

    Workspace initalWorkspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .properties(propertyGenerate)
            .build();
    WorkspaceFixtures.createWorkspaceInDb(initalWorkspace, workspaceDao);
    List<String> propertyUpdate = new ArrayList<>(Arrays.asList("foo", "foo1"));
    workspaceDao.deleteWorkspaceProperties(workspaceUuid, propertyUpdate);

    Map<String, String> updatedProperty = Map.of("xyz", "pqn");

    assertEquals(updatedProperty, workspaceDao.getWorkspace(workspaceUuid).getProperties());
  }

  @Test
  void workspaceCreateErrorDeserializes() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(workspaceUuid)
            .spendProfileId(spendProfileId)
            .build();
    var flightId = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace, flightId);
    var exception = new FieldSizeExceededException("This is a random ErrorReportException");
    workspaceDao.createWorkspaceFailure(
        workspaceUuid, flightId, exception, WsmResourceStateRule.BROKEN_ON_FAILURE);
    DbWorkspace errorWorkspace = workspaceDao.getDbWorkspace(workspaceUuid);
    // The deserialized exception will also include the original exception class name, so this
    // isn't an exact match.
    assertThat(errorWorkspace.getError().getMessage(), containsString(exception.getMessage()));
    assertEquals(exception.getStatusCode(), errorWorkspace.getError().getStatusCode());
  }

  @Nested
  class TestGcpCloudContext {

    @BeforeEach
    void setUp() {
      WorkspaceFixtures.createWorkspaceInDb(defaultRawlsWorkspace(workspaceUuid), workspaceDao);
    }

    @Test
    void createDeleteGcpCloudContext() {
      // Run the normal case
      WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
          workspaceDao, workspaceUuid, PROJECT_ID);
      WorkspaceUnitTestUtils.deleteCloudContextInDatabase(
          workspaceDao, workspaceUuid, CloudPlatform.GCP);

      // Mismatched flight id
      String flightId = UUID.randomUUID().toString();
      workspaceDao.createCloudContextStart(
          workspaceUuid, CloudPlatform.GCP, DEFAULT_GCP_SPEND_PROFILE_ID, flightId);

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

      // Make sure the context is still there; it will remain in the DELETING state
      // so normal retrieval in checkCloudContext will error.
      cloudContext = gcpCloudContextService.getGcpCloudContext(workspaceUuid);
      assertTrue(cloudContext.isPresent());
      assertNotNull(cloudContext.get().getCommonFields());
      assertEquals(WsmResourceState.DELETING, cloudContext.get().getCommonFields().state());
      assertEquals(flightId, cloudContext.get().getCommonFields().flightId());

      // finish the delete with a matching flight id
      workspaceDao.deleteCloudContextSuccess(workspaceUuid, CloudPlatform.GCP, flightId);
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
      assertTrue(WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao));
      assertThrows(
          WorkspaceNotFoundException.class, () -> workspaceDao.getWorkspace(workspaceUuid));

      assertTrue(gcpCloudContextService.getGcpCloudContext(workspaceUuid).isEmpty());
    }

    @Test
    void workspaceCreateErrorDeserializes() {
      var flightId = UUID.randomUUID().toString();
      workspaceDao.createCloudContextStart(
          workspaceUuid, CloudPlatform.GCP, DEFAULT_GCP_SPEND_PROFILE_ID, flightId);
      var exception = new FieldSizeExceededException("This is a random ErrorReportException");
      workspaceDao.createCloudContextFailure(
          workspaceUuid,
          CloudPlatform.GCP,
          flightId,
          exception,
          WsmResourceStateRule.BROKEN_ON_FAILURE);
      // Error deserializes from getCloudContext
      DbCloudContext errorDbContext =
          workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).get();
      // The deserialized exception will also include the original exception class name, so this
      // isn't an exact match.
      assertThat(errorDbContext.getError().getMessage(), containsString(exception.getMessage()));
      assertEquals(exception.getStatusCode(), errorDbContext.getError().getStatusCode());
      // Error deserializes from getWorkspaceDescription
      GcpCloudContext brokenGcpContext =
          workspaceDao.getWorkspaceDescription(workspaceUuid).getGcpCloudContext();
      assertThat(
          brokenGcpContext.getCommonFields().error().getMessage(),
          containsString(exception.getMessage()));
      assertEquals(
          exception.getStatusCode(), brokenGcpContext.getCommonFields().error().getStatusCode());
      // Error deserializes from getWorkspaceDescriptionMapFromIdList
      Map<UUID, DbWorkspaceDescription> descriptionMap =
          workspaceDao.getWorkspaceDescriptionMapFromIdList(
              Set.of(workspaceUuid), /* offset= */ 0, /* limit= */ 100);
      GcpCloudContext secondBrokenGcpContext =
          descriptionMap.get(workspaceUuid).getGcpCloudContext();
      assertThat(
          secondBrokenGcpContext.getCommonFields().error().getMessage(),
          containsString(exception.getMessage()));
      assertEquals(
          exception.getStatusCode(),
          secondBrokenGcpContext.getCommonFields().error().getStatusCode());
    }

    @Test
    void duplicateCloudContextCreateDoesNotDelete() {
      // Run the normal case
      WorkspaceUnitTestUtils.createGcpCloudContextInDatabase(
          workspaceDao, workspaceUuid, PROJECT_ID);
      // Later calls to createCloudContextFailure (e.g. from duplicate requests) should not undo
      // cloud contexts they did not create.
      workspaceDao.createCloudContextFailure(
          workspaceUuid,
          CloudPlatform.GCP,
          UUID.randomUUID().toString(),
          /* exception= */ null,
          WsmResourceStateRule.DELETE_ON_FAILURE);
      assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP).isPresent());
    }
  }

  private Workspace defaultRawlsWorkspace(UUID workspaceUuid) {
    return WorkspaceFixtures.buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);
  }

  private GcpCloudContext makeCloudContext() {
    return new GcpCloudContext(
        new GcpCloudContextFields(
            PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION),
        new CloudContextCommonFields(
                DEFAULT_GCP_SPEND_PROFILE_ID,
            WsmResourceState.READY,
            /* flightId= */ null,
            /* error= */ null));
  }

  private void checkCloudContext(Optional<GcpCloudContext> optionalContext) {
    assertTrue(optionalContext.isPresent());
    GcpCloudContext context = optionalContext.get();
    assertEquals(PROJECT_ID, context.getGcpProjectId());
    assertEquals(POLICY_OWNER, context.getSamPolicyOwner());
    assertEquals(POLICY_WRITER, context.getSamPolicyWriter());
    assertEquals(POLICY_READER, context.getSamPolicyReader());
    assertEquals(POLICY_APPLICATION, context.getSamPolicyApplication());
  }
}
