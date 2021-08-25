package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightStatus;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.model.WsmCloneResourceResult;

public class WorkspaceCloneUtils {

  private WorkspaceCloneUtils() {}

  public static WsmCloneResourceResult flightStatusToCloneResult(FlightStatus subflightStatus, WsmResource resource) {
    switch (subflightStatus) {
      default:
      case WAITING:
      case READY:
      case QUEUED:
      case READY_TO_RESTART:
      case RUNNING:
        throw new IllegalStateException(String.format("Unexpected status %s for finished flight",
            subflightStatus));
      case SUCCESS:
        if (CloningInstructions.COPY_NOTHING == resource.getCloningInstructions()) {
          return WsmCloneResourceResult.SKIPPED;
        } else {
          return WsmCloneResourceResult.SUCCEEDED;
        }
        break;
      case ERROR:
      case FATAL:
        return WsmCloneResourceResult.FAILED;
        break;
    }
  }
}
