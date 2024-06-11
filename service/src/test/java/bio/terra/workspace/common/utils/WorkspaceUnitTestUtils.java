package bio.terra.workspace.common.utils;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.GcpCloudContextFields;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;

/** Utilities for working with workspaces in unit tests. */
public class WorkspaceUnitTestUtils {
  public static final String POLICY_OWNER = "policy-owner";
  public static final String POLICY_WRITER = "policy-writer";
  public static final String POLICY_READER = "policy-reader";
  public static final String POLICY_APPLICATION = "policy-application";

  /**
   * Creates a workspaces without a cloud context and stores it in the database. Returns the
   * workspace id.
   */
  public static UUID createWorkspaceWithoutCloudContext(WorkspaceDao workspaceDao) {
    String flightId = UUID.randomUUID().toString();
    Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
    workspaceDao.createWorkspaceStart(workspace, /* applicationIds= */ flightId);
    workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId);
    return workspace.getWorkspaceId();
  }

  public static DbCloudContext makeDbCloudContext(CloudPlatform cloudPlatform, String json) {
    return new DbCloudContext()
        .cloudPlatform(cloudPlatform)
        .spendProfile(DEFAULT_GCP_SPEND_PROFILE_ID)
        .contextJson(json)
        .state(WsmResourceState.READY)
        .flightId(null)
        .error(null);
  }

  public static void deleteCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, CloudPlatform cloudPlatform) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.deleteCloudContextStart(workspaceUuid, cloudPlatform, flightId);
    workspaceDao.deleteCloudContextSuccess(workspaceUuid, cloudPlatform, flightId);
  }

  // GCP cloud context

  public static final String GCP_PROJECT_ID = "my-project-id";

  /**
   * Creates a workspaces with a GCP cloud context and stores it in the database. Returns the
   * workspace id.
   */
  public static UUID createWorkspaceWithGcpContext(WorkspaceDao workspaceDao) {
    UUID workspaceId = createWorkspaceWithoutCloudContext(workspaceDao);
    createGcpCloudContextInDatabase(workspaceDao, workspaceId, GCP_PROJECT_ID);
    return workspaceId;
  }

  /**
   * Creates the database artifact for a GCP cloud context without actually creating anything beyond
   * the database row.
   */
  public static void createGcpCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, String projectId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.GCP, DEFAULT_GCP_SPEND_PROFILE_ID, flightId);
    workspaceDao.createCloudContextSuccess(
        workspaceUuid,
        CloudPlatform.GCP,
        new GcpCloudContext(
                new GcpCloudContextFields(
                    projectId, POLICY_OWNER, POLICY_WRITER, POLICY_READER, POLICY_APPLICATION),
                new CloudContextCommonFields(
                        DEFAULT_GCP_SPEND_PROFILE_ID,
                    WsmResourceState.CREATING,
                    flightId,
                    /* error= */ null))
            .serialize(),
        flightId);
  }

  // Azure cloud context

  public static void createAzureCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, SpendProfileId billingProfileId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.AZURE, billingProfileId, flightId);
    workspaceDao.createCloudContextSuccess(
        workspaceUuid,
        CloudPlatform.AZURE,
        new AzureCloudContext(
                new AzureCloudContextFields("fake-tenant", "fake-subscription", "fake-mrg"),
                new CloudContextCommonFields(
                    billingProfileId, WsmResourceState.CREATING, flightId, null))
            .serialize(),
        flightId);
  }

  // AWS cloud context

  public static void createAwsCloudContextInDatabase(
      WorkspaceDao workspaceDao, UUID workspaceUuid, SpendProfileId billingProfileId) {
    String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(
        workspaceUuid, CloudPlatform.AWS, billingProfileId, flightId);
    workspaceDao.createCloudContextSuccess(
        workspaceUuid,
        CloudPlatform.AWS,
        new AwsCloudContext(
                new AwsCloudContextFields(
                    AwsTestUtils.MAJOR_VERSION,
                    AwsTestUtils.ORGANIZATION_ID,
                    AwsTestUtils.ACCOUNT_ID,
                    AwsTestUtils.TENANT_ALIAS,
                    AwsTestUtils.ENVIRONMENT_ALIAS,
                    AwsTestUtils.AWS_WORKSPACE_SECURITY_GROUPS),
                new CloudContextCommonFields(
                    billingProfileId, WsmResourceState.CREATING, flightId, null))
            .serialize(),
        flightId);
  }
}
