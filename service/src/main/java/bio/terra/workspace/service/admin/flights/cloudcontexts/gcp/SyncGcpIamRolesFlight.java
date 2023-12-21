package bio.terra.workspace.service.admin.flights.cloudcontexts.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IS_WET_RUN;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID_TO_GCP_PROJECT_ID_MAP;
import static java.util.Objects.requireNonNull;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudSyncRoleMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.UUID;

/** Flight to sync IAM roles of existing GCP projects. */
public class SyncGcpIamRolesFlight extends Flight {

  /**
   * @InheritDoc
   */
  public SyncGcpIamRolesFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    GcpCloudSyncRoleMapping gcpCloudSyncRoleMapping = appContext.getCloudSyncRoleMapping();
    CrlService crl = appContext.getCrlService();
    Map<UUID, String> projectIds =
        requireNonNull(
            inputParameters.get(WORKSPACE_ID_TO_GCP_PROJECT_ID_MAP, new TypeReference<>() {}));

    RetryRule cloudRetryRule = RetryRules.cloud();
    addStep(new SetupWorkingMapForUpdatedWorkspacesStep());
    boolean isWetRun = FlightUtils.getRequired(inputParameters, IS_WET_RUN, Boolean.class);
    for (Map.Entry<UUID, String> projectId : projectIds.entrySet()) {
      // Wrap IAM with WSM service account.
      addStep(
          new RetrieveGcpIamCustomRoleStep(
              gcpCloudSyncRoleMapping, crl.getIamCow(), projectId.getValue()),
          cloudRetryRule);

      addStep(
          new GcpIamCustomRolePatchStep(
              gcpCloudSyncRoleMapping,
              crl.getIamCow(),
              projectId.getKey(),
              projectId.getValue(),
              isWetRun),
          cloudRetryRule);
    }
  }
}
