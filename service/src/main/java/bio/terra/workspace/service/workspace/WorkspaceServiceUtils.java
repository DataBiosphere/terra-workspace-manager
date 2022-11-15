package bio.terra.workspace.service.workspace;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.exceptions.DuplicateWorkspaceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;
import javax.annotation.Nullable;

public class WorkspaceServiceUtils {

  /**
   * Common create workspace job builder. Used by createWorkspace and by clone WorkspaceCloneService
   * cannot call WorkspaceService due to circular autowiring so we have them share this code tidbit.
   *
   * <p>TODO: PF-2017 Probably a better way to organize this code...
   *
   * @param workspace workspace object to create
   * @param policies policies to set on the workspace
   * @param userRequest credentials for the create
   * @return JobBuilder for submitting the flight
   */
  public static JobBuilder buildCreateWorkspaceJob(
      JobService jobService,
      WorkspaceDao workspaceDao,
      Workspace workspace,
      @Nullable ApiTpsPolicyInputs policies,
      @Nullable List<String> applications,
      AuthenticatedUserRequest userRequest) {
    String workspaceUuid = workspace.getWorkspaceId().toString();
    String jobDescription =
        String.format(
            "Create workspace: name: '%s' id: '%s'  ",
            workspace.getDisplayName().orElse(""), workspaceUuid);

    // Before launching the flight, confirm the workspace does not already exist. This isn't perfect
    // if two requests come in at nearly the same time, but it prevents launching a flight when a
    // workspace already exists.
    if (workspaceDao.getWorkspaceIfExists(workspace.getWorkspaceId()).isPresent()) {
      throw new DuplicateWorkspaceException("Provided workspace ID is already in use");
    }

    JobBuilder createJob =
        jobService
            .newJob()
            .description(jobDescription)
            .flightClass(WorkspaceCreateFlight.class)
            .request(workspace)
            .userRequest(userRequest)
            .workspaceId(workspaceUuid)
            .operationType(OperationType.CREATE)
            .addParameter(
                WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspace.getWorkspaceStage().name())
            .addParameter(WorkspaceFlightMapKeys.POLICIES, policies)
            .addParameter(WorkspaceFlightMapKeys.APPLICATION_IDS, applications);

    if (workspace.getSpendProfileId().isPresent()) {
      createJob.addParameter(
          WorkspaceFlightMapKeys.SPEND_PROFILE_ID, workspace.getSpendProfileId().get().getId());
    }
    return createJob;
  }
}
