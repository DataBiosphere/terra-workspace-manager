package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import bio.terra.workspace.common.BaseTest;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

// This is a special test to make sure the application configuration works.
// We use a special profile to pick up a test application configuration.
@Tag("unit")
@AutoConfigureMockMvc
@ActiveProfiles({"unit-test", "configuration-test"})
public class ApplicationConfigurationTest extends BaseTest {

  @Autowired ApplicationDao appDao;

  private static final UUID LEO_UUID = UUID.fromString("4BD1D59D-5827-4375-A41D-BBC65919F269");
  private static final UUID CARMEN_UUID = UUID.fromString("EB9D37F5-BAD7-4951-AE9A-86B3F03F4DD7");
  private static final UUID TEST_WSM_APP_UUID =
      UUID.fromString("E4C0924A-3D7D-4D3D-8DE4-3D2CF50C3818");

  @Test
  public void configurationTest() {
    // This test has to be in sync with the contents of application-configuration-test.yml
    List<WsmApplication> wsmApps = appDao.listApplications();
    assertEquals(wsmApps.size(), 3);

    for (WsmApplication wsmApp : wsmApps) {
      if (wsmApp.getApplicationId().equals(LEO_UUID)) {
        checkLeo(wsmApp);
      } else if (wsmApp.getApplicationId().equals(CARMEN_UUID)) {
        checkCarmen(wsmApp);
      } else if (wsmApp.getApplicationId().equals(TEST_WSM_APP_UUID)) {
        checkTestWsmApp(wsmApp);
      } else {
        fail();
      }
    }
  }

  private void checkLeo(WsmApplication leoApp) {
    assertEquals(leoApp.getDisplayName(), "Leo");
    assertEquals(leoApp.getDescription(), "application execution framework");
    assertEquals(leoApp.getServiceAccount(), "leo@terra-dev.iam.gserviceaccount.com");
    assertEquals(leoApp.getState(), WsmApplicationState.OPERATING);
  }

  private void checkCarmen(WsmApplication carmenApp) {
    assertEquals(carmenApp.getDisplayName(), "Carmen");
    assertEquals(carmenApp.getDescription(), "musical performance framework");
    assertEquals(carmenApp.getServiceAccount(), "carmen@terra-dev.iam.gserviceaccount.com");
    assertEquals(carmenApp.getState(), WsmApplicationState.DEPRECATED);
  }

  private void checkTestWsmApp(WsmApplication testApp) {
    assertEquals(testApp.getDisplayName(), "TestWsmApp");
    assertEquals(testApp.getDescription(), "WSM test application");
    // Note that SAs (and all other emails) are always stored as lowercase strings.
    assertEquals(
        testApp.getServiceAccount(), "Elizabeth.Shadowmoon@test.firecloud.org".toLowerCase());
    assertEquals(testApp.getState(), WsmApplicationState.OPERATING);
  }
}
