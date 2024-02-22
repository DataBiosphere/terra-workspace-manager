package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.WorkspaceUnitTestUtils.makeDbCloudContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.services.cloudresourcemanager.v3.model.Project;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class GcpCloudContextUnitTest extends BaseSpringBootUnitTest {
  private static final String GCP_PROJECT_ID = "terra-wsm-t-clean-berry-5152";
  private static final String POLICY_OWNER = "policy-owner";
  private static final String POLICY_WRITER = "policy-writer";
  private static final String POLICY_READER = "policy-reader";
  private static final String POLICY_APPLICATION = "policy-application";

  @Autowired private WorkspaceDao workspaceDao;

  @Test
  void serdesTest() {
    // Case 1: successful V2 deserialization
    String v2Json =
        String.format(
            "{\"version\": 2, \"gcpProjectId\": \"%s\", \"samPolicyOwner\": \"%s\", \"samPolicyWriter\": \"%s\", \"samPolicyReader\": \"%s\", \"samPolicyApplication\": \"%s\" }",
            GCP_PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION);
    DbCloudContext dbCloudContext = makeDbCloudContext(CloudPlatform.GCP, v2Json);
    GcpCloudContext goodV2 = GcpCloudContext.deserialize(dbCloudContext);
    assertEquals(goodV2.getGcpProjectId(), GCP_PROJECT_ID);
    assertEquals(goodV2.getSamPolicyOwner(), POLICY_OWNER);
    assertEquals(goodV2.getSamPolicyWriter(), POLICY_WRITER);
    assertEquals(goodV2.getSamPolicyReader(), POLICY_READER);
    assertEquals(goodV2.getSamPolicyApplication(), POLICY_APPLICATION);

    // Case 2: bad V2 format
    String badV2Json =
        String.format(
            "{\"version\": 3, \"gcpProjectId\": \"%s\", \"samPolicyOwner\": \"%s\", \"samPolicyWriter\": \"%s\", \"samPolicyReader\": \"%s\", \"samPolicyApplication\": \"%s\" }",
            GCP_PROJECT_ID, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION);
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(makeDbCloudContext(CloudPlatform.GCP, badV2Json)),
        "Bad V2 JSON should throw");

    // Case 3: incomplete V2
    String incompleteV2Json =
        String.format("{\"version\": 2, \"gcpProjectId\": \"%s\"}", GCP_PROJECT_ID);
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(makeDbCloudContext(CloudPlatform.GCP, incompleteV2Json)),
        "Incomplete V2 JSON should throw");

    // Case 4: junk input
    String junkJson = "{\"foo\": 15, \"bar\": \"xyzzy\"}";
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> GcpCloudContext.deserialize(makeDbCloudContext(CloudPlatform.GCP, junkJson)),
        "Junk JSON should throw");
  }

  @Test
  public void deserialize_creatingContext_valid() {
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);

    String flightId = UUID.randomUUID().toString();

    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.GCP, new SpendProfileId("fake"), flightId);
    Optional<DbCloudContext> creatingContext =
        workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP);

    // After createCloudContextStart, a placeholder should exist in the DB.
    assertTrue(creatingContext.isPresent());
    GcpCloudContext cloudContext = GcpCloudContext.deserialize(creatingContext.get());
    assertNull(cloudContext.getContextFields());

    workspaceDao.createWorkspaceSuccess(workspaceUuid, flightId);
    WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao);
  }

  @Test
  void testErrorSerdes_errorReportExceptionWorks() {
    var ex = new ForbiddenException("this operation is strictly forbidden");

    // Create a broken cloud context in DB
    var workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    UUID workspaceUuid = workspace.workspaceId();
    WorkspaceFixtures.createWorkspaceInDb(workspace, workspaceDao);
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.GCP, new SpendProfileId("fake"), flightId);
    workspaceDao.createCloudContextFailure(
        workspaceUuid, CloudPlatform.GCP, flightId, ex, WsmResourceStateRule.BROKEN_ON_FAILURE);

    // Retrieve the broken cloud context which should have the error logged.
    Optional<DbCloudContext> maybeContext =
        workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.GCP);
    assertTrue(maybeContext.isPresent());
    assertEquals(WsmResourceState.BROKEN, maybeContext.get().getState());

    ErrorReportException exceptionFromDb = maybeContext.get().getError();
    assertEquals(ex.getStatusCode(), exceptionFromDb.getStatusCode());
    assertTrue(StringUtils.contains(exceptionFromDb.getMessage(), ex.getMessage()));

    WorkspaceFixtures.deleteWorkspaceFromDb(workspaceUuid, workspaceDao);
  }

  // Set up mocks for interacting with GCP to delete a project.
  // TODO(PF-1872): this should use shared mock code from CRL.
  private void setupCrlMocks() throws Exception {
    CloudResourceManagerCow mockResourceManager =
        Mockito.mock(CloudResourceManagerCow.class, Mockito.RETURNS_DEEP_STUBS);
    // Pretend the project is already being deleted.
    Mockito.when(mockResourceManager.projects().get(any()).execute())
        .thenReturn(new Project().setState("DELETE_IN_PROGRESS"));
    Mockito.when(mockCrlService().getCloudResourceManagerCow()).thenReturn(mockResourceManager);
  }
}
