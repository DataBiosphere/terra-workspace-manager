package bio.terra.workspace.service.workspace.flight.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.CloudContextHolder;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Updates the previously stored cloud context row, filling in the context JSON. */
public class CreateDbGcpCloudContextFinishStep implements Step {
  private final UUID workspaceUuid;
  private final GcpCloudContextService gcpCloudContextService;

  public CreateDbGcpCloudContextFinishStep(
      UUID workspaceUuid, GcpCloudContextService gcpCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    FlightUtils.validateRequiredEntries(
        flightContext.getWorkingMap(), GCP_PROJECT_ID, IAM_GROUP_EMAIL_MAP);

    String projectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    Map<WsmIamRole, String> workspaceRoleGroupsMap =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.IAM_GROUP_EMAIL_MAP, new TypeReference<>() {});

    GcpCloudContext context =
        new GcpCloudContext(
            projectId,
            workspaceRoleGroupsMap.get(WsmIamRole.OWNER),
            workspaceRoleGroupsMap.get(WsmIamRole.WRITER),
            workspaceRoleGroupsMap.get(WsmIamRole.READER),
            workspaceRoleGroupsMap.get(WsmIamRole.APPLICATION));

    gcpCloudContextService.createGcpCloudContextFinish(
        workspaceUuid, context, flightContext.getFlightId());

    CloudContextHolder cch = new CloudContextHolder();
    cch.setGcpCloudContext(context);

    FlightUtils.setResponse(flightContext, cch, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // We do not undo anything here. The create step will delete the row, if need be.
    return StepResult.getStepResultSuccess();
  }
}
