package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.RawDaoTestFixture;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketAttributes;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WsmApplicationService.WsmDbApplication;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;

public class ApplicationServiceTest extends BaseSpringBootUnitTest {
  private static final String LEO_ID = "4BD1D59D-5827-4375-A41D-BBC65919F269";
  private static final String CARMEN_ID = "Carmen";
  private static final String NORM_ID = "normal";
  private static final String DECOMMISSION_ID = "goodbye";
  private static final String UNKNOWN_ID = "never-heard_of_you";

  private static final WsmApplication LEO_APP =
      new WsmApplication()
          .applicationId(LEO_ID)
          .displayName("Leo")
          .description("application execution framework")
          .serviceAccount("leo@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.OPERATING);

  private static final WsmApplication CARMEN_APP =
      new WsmApplication()
          .applicationId(CARMEN_ID)
          .displayName("Carmen")
          .description("musical performance framework")
          .serviceAccount("carmen@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.DEPRECATED);

  private static final WsmApplication NORM_APP =
      new WsmApplication()
          .applicationId(NORM_ID)
          .displayName("Norm")
          .description("old house framework")
          .serviceAccount("norm@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.DECOMMISSIONED);

  private static final WsmApplication NORM_APP_RECOMMISSIONED =
      new WsmApplication()
          .applicationId(NORM_ID)
          .displayName("Norm")
          .description("old house framework")
          .serviceAccount("norm@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.OPERATING);

  @Autowired ApplicationDao appDao;
  @Autowired WsmApplicationService appService;
  @Autowired WorkspaceService workspaceService;
  @Autowired JobService jobService;
  @Autowired RawDaoTestFixture rawDaoTestFixture;
  @Autowired ResourceDao resourceDao;
  @Autowired WorkspaceDao workspaceDao;

  private Workspace workspace;
  private Workspace workspace2;

  @BeforeEach
  void setup() throws Exception {
    // Set up so all spend profile and workspace checks are successful
    when(mockSamService()
            .isAuthorized(
                Mockito.any(),
                Mockito.eq(SamResource.SPEND_PROFILE),
                Mockito.any(),
                Mockito.eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    when(mockSamService()
            .isAuthorized(
                Mockito.any(), Mockito.eq(SamResource.WORKSPACE), Mockito.any(), Mockito.any()))
        .thenReturn(true);
    when(mockSamService().getUserStatusInfo(USER_REQUEST))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));

    appService.enableTestMode();
    // Populate the applications - this should be idempotent since we are
    // re-creating the same configuration every time.
    Map<String, WsmDbApplication> dbAppMap = appService.buildAppMap();
    appService.processApp(LEO_APP, dbAppMap);
    appService.processApp(CARMEN_APP, dbAppMap);
    appService.processApp(NORM_APP, dbAppMap);

    // Create two workspaces
    workspace = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(workspace, null, null, null, USER_REQUEST);
    workspace2 = WorkspaceFixtures.buildMcWorkspace();
    workspaceService.createWorkspace(workspace2, null, null, null, USER_REQUEST);
  }

  @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
  @Test
  public void applicationEnableTest() {
    // Verify no apps enabled in workspace
    enumerateCheck(false, false, false);

    // enable leo - should work
    appService.enableWorkspaceApplication(USER_REQUEST, workspace, LEO_ID);

    // enable carmen - should fail - deprecated
    assertThrows(
        InvalidApplicationStateException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspace, CARMEN_ID));

    // enable norm - should fail - decommissioned
    assertThrows(
        InvalidApplicationStateException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspace, NORM_ID));

    enumerateCheck(true, false, false);

    // Use the DAO directly and skip the check to enable carmen so we can check that state.
    appDao.enableWorkspaceApplicationNoCheck(workspace.getWorkspaceId(), CARMEN_ID);
    enumerateCheck(true, true, false);

    // test the argument checking
    assertThrows(
        ApplicationNotFoundException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspace, UNKNOWN_ID));

    Workspace fakeWorkspace = WorkspaceFixtures.buildMcWorkspace();
    // This calls a service method, rather than a controller method, so it does not hit the authz
    // check. Instead, we validate that inserting this into the DB violates constraints.
    assertThrows(
        DataIntegrityViolationException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, fakeWorkspace, LEO_ID));

    // explicit get
    WsmWorkspaceApplication wsmApp = appService.getWorkspaceApplication(workspace, LEO_ID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertTrue(wsmApp.isEnabled());

    // get from workspace2
    wsmApp = appService.getWorkspaceApplication(workspace2, LEO_ID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertFalse(wsmApp.isEnabled());

    // enable Leo in workspace2
    appService.enableWorkspaceApplication(USER_REQUEST, workspace2, LEO_ID);

    // do the disables...
    appService.disableWorkspaceApplication(USER_REQUEST, workspace, LEO_ID);
    enumerateCheck(false, true, false);

    appService.disableWorkspaceApplication(USER_REQUEST, workspace, CARMEN_ID);
    enumerateCheck(false, false, false);

    // make sure Leo is still enabled in workspace2
    wsmApp = appService.getWorkspaceApplication(workspace2, LEO_ID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertTrue(wsmApp.isEnabled());

    // validate decommissioned apps can't be re-enabled
    appService.processApp(NORM_APP_RECOMMISSIONED, appService.buildAppMap());
    assertFalse(appService.getErrorList().isEmpty());
    // Most recent error should be caused by the decommission.
    assertTrue(
        appService
            .getErrorList()
            .get(appService.getErrorList().size() - 1)
            .contains("decommissioned"));
  }

  @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
  @Test
  public void decommissionAppTest() {
    WsmApplication decommissionApp =
        new WsmApplication()
            .applicationId(DECOMMISSION_ID)
            .displayName("DecommissionApp")
            .description("An app soon to be decommissioned")
            .serviceAccount("qwerty@terra-dev.iam.gserviceaccount.com")
            .state(WsmApplicationState.OPERATING);
    // Register the app to be decommissioned
    appService.processApp(decommissionApp, appService.buildAppMap());
    // Enable the test application in this workspace.
    appService.enableWorkspaceApplication(USER_REQUEST, workspace, DECOMMISSION_ID);
    // Create a fake app-owned referenced resource.
    UUID resourceId = createFakeResource(DECOMMISSION_ID);
    // Try to deprecate the app, should fail because this app has an associated resource.
    decommissionApp.state(WsmApplicationState.DECOMMISSIONED);
    appService.processApp(decommissionApp, appService.buildAppMap());
    assertFalse(appService.getErrorList().isEmpty());
    assertTrue(
        appService
            .getErrorList()
            .get(appService.getErrorList().size() - 1)
            .contains("associated resources"));
    // Delete the resource
    resourceDao.deleteReferencedResource(workspace.getWorkspaceId(), resourceId);
    // try to decommission the app again, should succeed this time
    appService.processApp(decommissionApp, appService.buildAppMap());
    WsmWorkspaceApplication readApp =
        appService.getWorkspaceApplication(workspace, decommissionApp.getApplicationId());
    assertEquals(WsmApplicationState.DECOMMISSIONED, readApp.getApplication().getState());
  }

  // Create a fake application-controlled resource in this test's workspace. Returns the resourceId.
  private UUID createFakeResource(String appId) {
    UUID resourceId = UUID.randomUUID();
    ControlledGcsBucketAttributes fakeAttributes =
        new ControlledGcsBucketAttributes("fake-bucket-name");
    rawDaoTestFixture.storeResource(
        workspace.getWorkspaceId().toString(),
        CloudPlatform.GCP.toSql(),
        resourceId.toString(),
        "resource_name",
        "resource_description",
        StewardshipType.CONTROLLED.toSql(),
        WsmResourceType.CONTROLLED_GCP_GCS_BUCKET.toSql(),
        WsmResourceFamily.GCS_BUCKET.toSql(),
        CloningInstructions.COPY_NOTHING.toSql(),
        DbSerDes.toJson(fakeAttributes),
        AccessScopeType.ACCESS_SCOPE_SHARED.toSql(),
        ManagedByType.MANAGED_BY_APPLICATION.toSql(),
        appId,
        null,
        PrivateResourceState.NOT_APPLICABLE.toSql());
    return resourceId;
  }

  private void enumerateCheck(boolean leoEnabled, boolean carmenEnabled, boolean normEnabled) {
    List<WsmWorkspaceApplication> wsmAppList =
        appService.listWorkspaceApplications(workspace, 0, 10);
    // There may be stray applications in the DB, so we make sure that we at least have ours
    assertThat(wsmAppList.size(), greaterThanOrEqualTo(3));
    for (WsmWorkspaceApplication wsmApp : wsmAppList) {
      switch (wsmApp.getApplication().getApplicationId()) {
        case LEO_ID -> assertEquals(leoEnabled, wsmApp.isEnabled());
        case CARMEN_ID -> assertEquals(carmenEnabled, wsmApp.isEnabled());
        case NORM_ID -> assertEquals(normEnabled, wsmApp.isEnabled());
      }
    }
  }
}
