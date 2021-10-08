package bio.terra.workspace.service.workspace;

import bio.terra.workspace.app.configuration.external.WsmApplicationConfiguration;
import bio.terra.workspace.app.configuration.external.WsmApplicationConfiguration.App;
import bio.terra.workspace.db.ApplicationDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.exceptions.InvalidApplicationConfigException;
import bio.terra.workspace.service.workspace.flight.disable.application.DisableApplicationFlight;
import bio.terra.workspace.service.workspace.flight.disable.application.DisableApplicationKeys;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for workspace application management. It includes methods for initial configuration,
 * responding the REST API controller, and for internal application events.
 */
@Component
public class WsmApplicationService {
  private static final Logger logger = LoggerFactory.getLogger(WsmApplicationService.class);

  private final ApplicationDao applicationDao;
  private final JobService jobService;
  private final WsmApplicationConfiguration wsmApplicationConfiguration;
  private final WorkspaceService workspaceService;
  // -- Testing Support --
  // Unlike most code, the configuration code runs at startup time and does not have any output
  // beyond writing to the log. In order to test it, we introduce a test mode and wrap the error
  // logging. When test mode is enabled, the wrapper saves a string array of log messages in
  // addition to calling the logger.
  private boolean testMode = false;
  private List<String> errorList;

  @Autowired
  public WsmApplicationService(
      ApplicationDao applicationDao,
      JobService jobService,
      WsmApplicationConfiguration wsmApplicationConfiguration,
      WorkspaceService workspaceService) {
    this.applicationDao = applicationDao;
    this.jobService = jobService;
    this.wsmApplicationConfiguration = wsmApplicationConfiguration;
    this.workspaceService = workspaceService;
  }

  // -- REST API Methods -- //

  public void disableWorkspaceApplication(
      AuthenticatedUserRequest userRequest,
      UUID workspaceId,
      UUID applicationId,
      String resultPath,
      String jobId) {

    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_OWN_ACTION);

    jobService
        .newJob(
            "Disable application " + applicationId + " from workspace " + workspaceId,
            jobId,
            DisableApplicationFlight.class,
            null,
            userRequest)
        .addParameter(DisableApplicationKeys.WORKSPACE_ID, workspaceId.toString())
        .addParameter(DisableApplicationKeys.APPLICATION_ID, applicationId.toString())
        .submit();
  }

  public WsmWorkspaceApplication enableWorkspaceApplication(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID applicationId) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_OWN_ACTION);

    return applicationDao.enableWorkspaceApplication(workspaceId, applicationId);
  }

  public WsmWorkspaceApplication getWorkspaceApplication(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID applicationId) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);

    return applicationDao.getWorkspaceApplication(workspaceId, applicationId);
  }

  public List<WsmWorkspaceApplication> listWorkspaceApplications(
      AuthenticatedUserRequest userRequest, UUID workspaceId, int offset, int limit) {
    workspaceService.validateWorkspaceAndAction(
        userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);

    return applicationDao.listWorkspaceApplications(workspaceId, offset, limit);
  }

  // -- Configuration Processing Methods -- //

  /**
   * Configure applications mapping from the incoming configuration to the current database context.
   */
  public void configure() {
    Map<UUID, WsmDbApplication> dbAppMap = buildAppMap();

    // Read the config and validate the inputs. Log errors. Badly formed configs are error logged
    // and not included on the resulting list.
    List<WsmApplication> configApps = listFromConfig();

    // Apply each configuration
    for (var configApp : configApps) {
      processApp(configApp, dbAppMap);
    }

    // Log any apps in the database that were not in the configuration
    checkMissingConfig(dbAppMap);
  }

  @VisibleForTesting
  void checkMissingConfig(Map<UUID, WsmDbApplication> dbAppMap) {
    List<String> missing = new ArrayList<>();
    for (WsmDbApplication dbApp : dbAppMap.values()) {
      if (!dbApp.isMatched()) {
        missing.add(dbApp.getWsmApplication().getApplicationId().toString());
      }
    }
    if (missing.size() > 0) {
      logError(
          "Invalid application configuration: missing application(s): "
              + String.join(", ", missing));
    }
  }

  /**
   * Process one application configuration.
   *
   * @param configApp app from configuration
   * @param dbAppMap map of apps from database
   */
  @VisibleForTesting
  void processApp(WsmApplication configApp, Map<UUID, WsmDbApplication> dbAppMap) {
    WsmDbApplication dbApp = dbAppMap.get(configApp.getApplicationId());

    // If the application id is not in the database, create it.
    if (dbApp == null) {
      try {
        applicationDao.createApplication(configApp);
        logInfo("Created application " + configApp.getApplicationId());
      } catch (Exception e) {
        logError("Failed to create application: " + configApp.getApplicationId());
      }
      return;
    }

    // Detect duplicate configurations
    if (dbApp.isMatched()) {
      logError(
          "Invalid application configuration: ignoring duplicate configuration: "
              + configApp.getApplicationId());
      return;
    }
    dbApp.setMatched(true);

    // Do nothing if no config change
    if (configApp.equals(dbApp.getWsmApplication())) {
      logInfo("No change to application configuration: " + configApp.getApplicationId());
      return;
    }

    // Something changed, so we need to update the config. Make sure the state change is legal.
    // There are two checks to make.

    // Check 1: if we are moving from (operating, deprecated) to decommissioned, then we need
    // to make sure there are no resources owned by the application.
    if (dbApp.getWsmApplication().getState() != WsmApplicationState.DECOMMISSIONED
        && configApp.getState() == WsmApplicationState.DECOMMISSIONED) {
      if (applicationDao.applicationInUse(configApp.getApplicationId())) {
        logError(
            "Invalid application configuration: application "
                + configApp.getApplicationId()
                + " has associated resources so cannot be decommissioned");
        return;
      }
    }

    // Check 2: it is invalid to move from decommissioned to (operating, deprecated).
    if (dbApp.getWsmApplication().getState() == WsmApplicationState.DECOMMISSIONED
        && configApp.getState() != WsmApplicationState.DECOMMISSIONED) {
      logError("Invalid application configuration: application has been decommissioned");
      return;
    }

    // Do the update in the database
    applicationDao.updateApplication(configApp);
    logInfo("Updated application configuration: " + configApp.getApplicationId());
  }

  @VisibleForTesting
  Map<UUID, WsmDbApplication> buildAppMap() {
    List<WsmApplication> dbApps = applicationDao.listApplications();
    Map<UUID, WsmDbApplication> dbAppMap = new HashMap<>();
    for (WsmApplication app : dbApps) {
      dbAppMap.put(app.getApplicationId(), new WsmDbApplication(app));
    }
    return dbAppMap;
  }

  private List<WsmApplication> listFromConfig() {
    // scan the config building WsmApplications
    List<WsmApplication> configApps = new ArrayList<>();
    for (var config : wsmApplicationConfiguration.getConfigurations()) {
      appFromConfig(config).ifPresent(configApps::add);
    }
    return configApps;
  }

  @VisibleForTesting
  Optional<WsmApplication> appFromConfig(App config) {
    try {
      if (config.getIdentifier() == null
          || config.getServiceAccount() == null
          || config.getState() == null) {
        logError(
            "Invalid application configuration: missing some required fields (identifier, service-account, state)");
        return Optional.empty();
      }

      UUID applicationId = UUID.fromString(config.getIdentifier());
      WsmApplicationState state = WsmApplicationState.fromString(config.getState());
      if (!EmailValidator.getInstance(false, true).isValid(config.getServiceAccount())) {
        logError("Invalid application configuration: service account is not a valid email address");
        return Optional.empty();
      }

      return Optional.of(
          new WsmApplication()
              .applicationId(applicationId)
              .displayName(config.getName())
              .description(config.getDescription())
              .serviceAccount(config.getServiceAccount())
              .state(state));

    } catch (IllegalArgumentException e) {
      logError("Invalid application configuration: invalid UUID format", e);
    } catch (InvalidApplicationConfigException e) {
      logError(
          "Invalid application configuration: state must be operating, deprecated, or decommissioned",
          e);
    }
    return Optional.empty();
  }

  @VisibleForTesting
  void enableTestMode() {
    this.testMode = true;
    this.errorList = new ArrayList<>();
  }

  @VisibleForTesting
  List<String> getErrorList() {
    return errorList;
  }

  private void logError(String s, Throwable e) {
    if (testMode) {
      errorList.add(s);
    }
    logger.error(s, e);
  }

  private void logError(String s) {
    if (testMode) {
      errorList.add(s);
    }
    logger.error(s);
  }

  private void logInfo(String s) {
    if (testMode) {
      errorList.add(s);
    }
    logger.info(s);
  }

  // A map of these are used to easily match incoming config with the database config
  @VisibleForTesting
  static class WsmDbApplication {
    private final WsmApplication wsmApplication;
    private boolean matched; // set true if the configuration matches this database entry

    WsmDbApplication(WsmApplication app) {
      wsmApplication = app;
      matched = false;
    }

    public WsmApplication getWsmApplication() {
      return wsmApplication;
    }

    public boolean isMatched() {
      return matched;
    }

    public void setMatched(boolean matched) {
      this.matched = matched;
    }
  }
}
