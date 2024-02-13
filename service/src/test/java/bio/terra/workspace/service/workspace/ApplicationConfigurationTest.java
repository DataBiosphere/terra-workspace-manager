package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseSpringBootTest;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

// This is a special test to make sure the application configuration works.
// We use a special profile to pick up a test application configuration.
@Disabled
@Tag("unit")
@ActiveProfiles({"unit-test", "configuration-test"})
public class ApplicationConfigurationTest extends BaseSpringBootTest {

  @Autowired ApplicationDao appDao;

  private static final String SAN_DIEGO_ID = "SanDiego";
  private static final String CARMEN_ID = "Carmen";

  @Test
  public void configurationTest() {
    // This test has to be in sync with the contents of application-configuration-test.yml
    List<WsmApplication> wsmApps = appDao.listApplications();
    assertTrue(wsmApps.size() >= 2);

    for (WsmApplication wsmApp : wsmApps) {
      if (wsmApp.getApplicationId().equals(SAN_DIEGO_ID)) {
        checkSanDiego(wsmApp);
      } else if (wsmApp.getApplicationId().equals(CARMEN_ID)) {
        checkCarmen(wsmApp);
      }
    }
  }

  private void checkSanDiego(WsmApplication sdApp) {
    assertEquals("SanDiego", sdApp.getApplicationId());
    assertEquals("Sunny", sdApp.getDisplayName());
    assertEquals("beachgoing framework", sdApp.getDescription());
    assertEquals("sandiego@terra-dev.iam.gserviceaccount.com", sdApp.getServiceAccount());
    assertEquals(WsmApplicationState.OPERATING, sdApp.getState());
  }

  private void checkCarmen(WsmApplication carmenApp) {
    assertEquals("Carmen", carmenApp.getApplicationId());
    assertEquals("Carmen", carmenApp.getDisplayName());
    assertEquals("musical performance framework", carmenApp.getDescription());
    assertEquals("carmen@terra-dev.iam.gserviceaccount.com", carmenApp.getServiceAccount());
    assertEquals(WsmApplicationState.DEPRECATED, carmenApp.getState());
  }
}
