package bio.terra.workspace.service.workspace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.app.configuration.external.WsmApplicationConfiguration.App;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.workspace.WsmApplicationService.WsmDbApplication;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;

public class ApplicationUnitTest extends BaseUnitTest {

  @Autowired WsmApplicationService appService;
  @Autowired ApplicationDao appDao;

  // These strings have to match the text in WsmApplicationService or the test will fail
  private static final String ERROR_MISSING_REQUIRED =
      "Invalid application configuration: missing some required fields (identifier, service-account, state)";
  private static final String ERROR_BAD_UUID =
      "Invalid application configuration: invalid UUID format";
  private static final String ERROR_BAD_EMAIL =
      "Invalid application configuration: service account is not a valid email address";
  private static final String ERROR_BAD_STATE =
      "Invalid application configuration: state must be operating, deprecated, or decommissioned";

  private static final String INFO_CREATED = "Created application";
  private static final String INFO_NOCHANGE = "No change to application configuration";
  private static final String INFO_UPDATED = "Updated application configuration";
  private static final String ERROR_DECOMMISSIONED =
      "Invalid application configuration: application has been decommissioned";
  private static final String ERROR_DUPLICATE =
      "Invalid application configuration: ignoring duplicate configuration";
  private static final String ERROR_CREATE_FAILED = "Failed to create application";
  private static final String ERROR_MISSING_APP =
      "Invalid application configuration: missing application(s): ";

  private static final String GOOD_UUID_STRING = "4BD1D59D-5827-4375-A41D-BBC65919F269";
  private static final UUID GOOD_UUID = UUID.fromString(GOOD_UUID_STRING);
  private static final String GOOD_EMAIL = "foo@kripalu.yoga";
  private static final String GOOD_STATE = WsmApplicationState.OPERATING.name();
  private static final String GOOD_NAME = "BestApplication";
  private static final String GOOD_DESC = "The Best of All Possible Applications";
  private static final String BAD_DATA = "xyzzy";

  @Test
  public void configValidationTest() {
    // The App class is what we get from the configuration file. This test verifies that the
    // config validation is working properly.

    // Test with empty object
    App testApp = new App();
    configValidationFail(testApp, ERROR_MISSING_REQUIRED);

    // Test with missing UUID
    testApp = makeApp(null, GOOD_EMAIL, GOOD_STATE);
    configValidationFail(testApp, ERROR_MISSING_REQUIRED);

    // Test with missing service account
    testApp = makeApp(GOOD_UUID_STRING, null, GOOD_STATE);
    configValidationFail(testApp, ERROR_MISSING_REQUIRED);

    // Test with missing state
    testApp = makeApp(GOOD_UUID_STRING, GOOD_EMAIL, null);
    configValidationFail(testApp, ERROR_MISSING_REQUIRED);

    // Test with bad UUID
    testApp = makeApp(BAD_DATA, GOOD_EMAIL, GOOD_STATE);
    configValidationFail(testApp, ERROR_BAD_UUID);

    // Test with bad email
    testApp = makeApp(GOOD_UUID_STRING, BAD_DATA, GOOD_STATE);
    configValidationFail(testApp, ERROR_BAD_EMAIL);

    // Test with bad state
    testApp = makeApp(GOOD_UUID_STRING, GOOD_EMAIL, BAD_DATA);
    configValidationFail(testApp, ERROR_BAD_STATE);

    // Test with everything good
    testApp = makeApp(GOOD_UUID_STRING, GOOD_EMAIL, GOOD_STATE);
    configValidationSuccess(testApp);

    // Test with name and desc filled in
    testApp = makeApp(GOOD_UUID_STRING, GOOD_EMAIL, GOOD_STATE);
    testApp.setName(GOOD_NAME);
    testApp.setDescription(GOOD_DESC);
    WsmApplication wsmApp = configValidationSuccess(testApp);
    assertEquals(GOOD_NAME, wsmApp.getDisplayName());
    assertEquals(GOOD_DESC, wsmApp.getDescription());
  }

  // This test writes to the database, so conflicts with the missingConfigTest
  @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
  @Test
  public void processAppTest() {
    // Each "pass" in the test represents starting up with a different configuration.

    // Start with an empty map == no data in the database
    Map<UUID, WsmDbApplication> dbAppMap = new HashMap<>();
    WsmApplication wsmApp = makeWsmApp();

    // -- First Pass --
    // Create a new application in the database
    appService.enableTestMode();
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(0, INFO_CREATED);

    // Retrieve apps from the database and validate the data round-trip
    // There can be stray applications in the database, so we use greater/equal assert

    List<WsmApplication> wsmApps = appDao.listApplications();
    assertThat(wsmApps.size(), greaterThanOrEqualTo(1));
    assertTrue(findEqualApp(wsmApps, wsmApp));

    // Trying to create a duplicate new application. This is not caught in the map, but
    // the database create fails on a PK constraint.
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(1, ERROR_CREATE_FAILED);

    // -- Second pass --
    // Rebuild the db app map with wsmApp in it
    dbAppMap = appService.buildAppMap();
    appService.enableTestMode();
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(0, INFO_NOCHANGE);

    // Process it again with the same app map; should flag it as a duplicate
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(1, ERROR_DUPLICATE);

    // -- 3rd pass -- update name and desc
    dbAppMap = appService.buildAppMap();
    appService.enableTestMode();
    wsmApp.displayName(GOOD_NAME);
    wsmApp.description(GOOD_DESC);
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(0, INFO_UPDATED);

    // -- 4th pass -- State transition to deprecated
    stateTransition(WsmApplicationState.DEPRECATED, INFO_UPDATED);

    // -- 5th pass -- State transition back to operating
    stateTransition(WsmApplicationState.OPERATING, INFO_UPDATED);

    // -- 6th pass -- State transition to decommissioned
    stateTransition(WsmApplicationState.DECOMMISSIONED, INFO_UPDATED);

    // -- 7th pass -- Cannot transition from decommissioned
    stateTransition(WsmApplicationState.OPERATING, ERROR_DECOMMISSIONED);

    // -- 8th pass -- Cannot transition from decommissioned
    stateTransition(WsmApplicationState.DEPRECATED, ERROR_DECOMMISSIONED);
  }

  // This test writes to the database, so conflicts with the processAppTest
  @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
  @Test
  public void missingConfigTest() {
    // Create two applications
    Map<UUID, WsmDbApplication> dbAppMap = new HashMap<>();
    WsmApplication wsmApp = makeWsmApp();
    appService.enableTestMode();
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(0, INFO_CREATED);

    WsmApplication wsmApp2 =
        new WsmApplication()
            .applicationId(UUID.fromString("EF580D18-5CB4-4BEF-A5C9-DB5F30EBE368"))
            .serviceAccount("bar@kripalu.yoga")
            .state(WsmApplicationState.OPERATING);
    appService.processApp(wsmApp2, dbAppMap);
    assertMessage(1, INFO_CREATED);

    // 2nd pass - only process 1
    dbAppMap = appService.buildAppMap();
    appService.enableTestMode();
    appService.processApp(wsmApp2, dbAppMap);
    assertMessage(0, INFO_NOCHANGE);

    appService.checkMissingConfig(dbAppMap);
    assertMessage(1, ERROR_MISSING_APP);

    // 3rd pass - process none
    dbAppMap = appService.buildAppMap();
    appService.checkMissingConfig(dbAppMap);
    assertMessage(2, ERROR_MISSING_APP);
  }

  private WsmApplication makeWsmApp() {
    return new WsmApplication()
        .applicationId(GOOD_UUID)
        .serviceAccount(GOOD_EMAIL)
        .state(WsmApplicationState.OPERATING);
  }

  private void assertMessage(int index, String prefix) {
    List<String> errorList = appService.getErrorList();
    assertTrue(StringUtils.startsWith(errorList.get(index), prefix));
  }

  private void stateTransition(WsmApplicationState targetState, String message) {
    WsmApplication wsmApp = makeWsmApp();
    wsmApp.state(targetState);
    Map<UUID, WsmDbApplication> dbAppMap = appService.buildAppMap();
    appService.enableTestMode();
    appService.processApp(wsmApp, dbAppMap);
    assertMessage(0, message);
  }

  private App makeApp(String identifier, String serviceAccount, String state) {
    App configApp = new App();
    configApp.setIdentifier(identifier);
    configApp.setServiceAccount(serviceAccount);
    configApp.setState(state);
    return configApp;
  }

  private void configValidationFail(App testApp, String expectedError) {
    appService.enableTestMode();
    Optional<WsmApplication> wsmApp = appService.appFromConfig(testApp);
    assertTrue(wsmApp.isEmpty());
    List<String> errorList = appService.getErrorList();
    assertEquals(errorList.get(0), expectedError);
  }

  private WsmApplication configValidationSuccess(App testApp) {
    appService.enableTestMode();
    Optional<WsmApplication> wsmAppOpt = appService.appFromConfig(testApp);
    assertTrue(wsmAppOpt.isPresent());
    List<String> errorList = appService.getErrorList();
    assertEquals(errorList.size(), 0);
    WsmApplication wsmApp = wsmAppOpt.get();
    assertEquals(wsmApp.getApplicationId(), GOOD_UUID);
    assertEquals(wsmApp.getServiceAccount(), GOOD_EMAIL);
    assertEquals(wsmApp.getState().name(), GOOD_STATE);
    return wsmApp;
  }

  private boolean findEqualApp(List<WsmApplication> wsmApps, WsmApplication wsmApp) {
    for (WsmApplication testApp : wsmApps) {
      if (testApp.equals(wsmApp)) {
        return true;
      }
    }
    return false;
  }
}
