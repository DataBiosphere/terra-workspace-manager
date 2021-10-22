package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.db.exception.WorkspaceNotFoundException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WsmApplicationService.WsmDbApplication;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

// Test notes
// Populate two applications
// Create workspace
// Enumerate/get
// enable operating - works
// enable deprecated - fails
// enableNocheck deprecated - works
// Enumerate/get
// disable deprecated
// disable operating
// Enumerate/get

public class ApplicationServiceTest extends BaseUnitTest {
  private static final UUID LEO_UUID = UUID.fromString("4BD1D59D-5827-4375-A41D-BBC65919F269");
  private static final UUID CARMEN_UUID = UUID.fromString("EB9D37F5-BAD7-4951-AE9A-86B3F03F4DD7");
  private static final UUID NORM_UUID = UUID.fromString("DB186A44-2881-4CD6-9D1B-65441574C3B1");
  private static final UUID UNKNOWN_UUID = UUID.fromString("DB186A44-2881-4CD6-9D1B-65441574C3B2");

  private static final WsmApplication LEO_APP =
      new WsmApplication()
          .applicationId(LEO_UUID)
          .displayName("Leo")
          .description("application execution framework")
          .serviceAccount("leo@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.OPERATING);

  private static final WsmApplication CARMEN_APP =
      new WsmApplication()
          .applicationId(CARMEN_UUID)
          .displayName("Carmen")
          .description("musical performance framework")
          .serviceAccount("carmen@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.DEPRECATED);

  private static final WsmApplication NORM_APP =
      new WsmApplication()
          .applicationId(NORM_UUID)
          .displayName("Norm")
          .description("old house framework")
          .serviceAccount("norm@terra-dev.iam.gserviceaccount.com")
          .state(WsmApplicationState.DECOMMISSIONED);

  /** A fake authenticated user request. */
  private static final AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest()
          .token(Optional.of("fake-token"))
          .email("fake@email.com")
          .subjectId("fakeID123");

  @Autowired ApplicationDao appDao;
  @Autowired WsmApplicationService appService;
  @Autowired WorkspaceService workspaceService;
  @Autowired JobService jobService;

  /** Mock SamService does nothing for all calls that would throw if unauthorized. */
  @MockBean private SamService mockSamService;

  private UUID workspaceId;
  private UUID workspaceId2;

  @BeforeEach
  void setup() throws Exception {
    // Set up so all spend profile and workspace checks are successful
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_RESOURCE),
                Mockito.any(),
                Mockito.eq(SamConstants.SPEND_PROFILE_LINK_ACTION)))
        .thenReturn(true);
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                Mockito.eq(SamConstants.SAM_WORKSPACE_RESOURCE),
                Mockito.any(),
                Mockito.any()))
        .thenReturn(true);

    // Populate the applications - this should be idempotent since we are
    // re-creating the same configuration every time.
    Map<UUID, WsmDbApplication> dbAppMap = appService.buildAppMap();
    appService.processApp(LEO_APP, dbAppMap);
    appService.processApp(CARMEN_APP, dbAppMap);
    appService.processApp(NORM_APP, dbAppMap);

    // Create two workspaces
    workspaceId = UUID.randomUUID();
    var request =
        WorkspaceRequest.builder()
            .workspaceId(workspaceId)
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();

    workspaceService.createWorkspace(request, USER_REQUEST);

    workspaceId2 = UUID.randomUUID();
    request =
        WorkspaceRequest.builder()
            .workspaceId(workspaceId2)
            .spendProfileId(Optional.empty())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .build();
    workspaceService.createWorkspace(request, USER_REQUEST);
  }

  @Test
  public void applicationEnableTest() {
    // Verify no apps enabled in workspace
    enumerateCheck(false, false, false);

    // enable leo - should work
    appService.enableWorkspaceApplication(USER_REQUEST, workspaceId, LEO_UUID);

    // enable carmen - should fail - deprecated
    assertThrows(
        InvalidApplicationStateException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspaceId, CARMEN_UUID));

    // enable norm - should fail - decommissioned
    assertThrows(
        InvalidApplicationStateException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspaceId, NORM_UUID));

    enumerateCheck(true, false, false);

    // Use the DAO directly and skip the check to enable carmen so we can check that state.
    appDao.enableWorkspaceApplicationNoCheck(workspaceId, CARMEN_UUID);
    enumerateCheck(true, true, false);

    // test the argument checking
    assertThrows(
        ApplicationNotFoundException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, workspaceId, UNKNOWN_UUID));
    assertThrows(
        WorkspaceNotFoundException.class,
        () -> appService.enableWorkspaceApplication(USER_REQUEST, UNKNOWN_UUID, LEO_UUID));

    // explicit get
    WsmWorkspaceApplication wsmApp =
        appService.getWorkspaceApplication(USER_REQUEST, workspaceId, LEO_UUID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertTrue(wsmApp.isEnabled());

    // get from workspace2
    wsmApp = appService.getWorkspaceApplication(USER_REQUEST, workspaceId2, LEO_UUID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertFalse(wsmApp.isEnabled());

    // enable Leo in workspace2
    appService.enableWorkspaceApplication(USER_REQUEST, workspaceId2, LEO_UUID);

    // do the disables...
    appService.disableWorkspaceApplication(USER_REQUEST, workspaceId, LEO_UUID);
    enumerateCheck(false, true, false);

    appService.disableWorkspaceApplication(USER_REQUEST, workspaceId, CARMEN_UUID);
    enumerateCheck(false, false, false);

    // make sure Leo is still enabled in workspace2
    wsmApp = appService.getWorkspaceApplication(USER_REQUEST, workspaceId2, LEO_UUID);
    assertEquals(LEO_APP, wsmApp.getApplication());
    assertTrue(wsmApp.isEnabled());
  }

  private void enumerateCheck(boolean leoEnabled, boolean carmenEnabled, boolean normEnabled) {
    List<WsmWorkspaceApplication> wsmAppList =
        appService.listWorkspaceApplications(USER_REQUEST, workspaceId, 0, 10);
    // There may be stray applications in the DB, so we make sure that we at least have ours
    assertThat(wsmAppList.size(), greaterThanOrEqualTo(3));
    for (WsmWorkspaceApplication wsmApp : wsmAppList) {
      if (wsmApp.getApplication().getApplicationId().equals(LEO_UUID)) {
        assertEquals(leoEnabled, wsmApp.isEnabled());
      } else if (wsmApp.getApplication().getApplicationId().equals(CARMEN_UUID)) {
        assertEquals(carmenEnabled, wsmApp.isEnabled());
      } else if (wsmApp.getApplication().getApplicationId().equals(NORM_UUID)) {
        assertEquals(normEnabled, wsmApp.isEnabled());
      }
    }
  }
}
