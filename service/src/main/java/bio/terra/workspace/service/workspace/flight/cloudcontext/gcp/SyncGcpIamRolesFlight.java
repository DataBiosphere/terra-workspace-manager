package bio.terra.workspace.service.workspace.flight.cloudcontext.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_IDS;
import static java.util.Objects.requireNonNull;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.crl.CrlService;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;

/** Flight to sync IAM roles of existing GCP projects. */
public class SyncGcpIamRolesFlight extends Flight {

  /** @InheritDoc */
  public SyncGcpIamRolesFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    FlightBeanBag appContext = FlightBeanBag.getFromObject(applicationContext);
    CrlService crl = appContext.getCrlService();
    ArrayList<String> projectIds =
        requireNonNull(inputParameters.get(GCP_PROJECT_IDS, new TypeReference<>() {}));

    RetryRule cloudRetryRule = RetryRules.cloud();
    for (String projectId : projectIds) {
      // Wrap IAM with WSM service account.
      addStep(new RetrieveGcpIamCustomRoleStep(crl.getIamCow(), projectId), cloudRetryRule);
      addStep(new GcpIamCustomRolePatchStep(crl.getIamCow(), projectId), cloudRetryRule);
    }
  }
}
