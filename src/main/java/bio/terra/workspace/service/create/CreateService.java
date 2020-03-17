package bio.terra.workspace.service.create;

import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.create.flight.WorkspaceCreateFlight;
import bio.terra.workspace.service.create.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateService {
  private JobService jobService;

  @Autowired
  public CreateService(JobService jobService) {
    this.jobService = jobService;
  }

  public void createWorkspace(CreateWorkspaceRequestBody body, AuthenticatedUserRequest userReq) {

    // CreatedWorkspace workspace = new CreatedWorkspace();
    UUID workspaceId = body.getId();
    String description = "Create workspace " + workspaceId.toString();
    JobControl jobControl = body.getJobControl();
    // TODO: Need to support pubsub notifications here.
    JobBuilder createJob =
        jobService
            .newJob(description, jobControl.getJobid(), WorkspaceCreateFlight.class, body, userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    if (body.getSpendProfile().isPresent()) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, body.getSpendProfile().get());
    }
    createJob.submit();
  }
}
