package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for GCP objects. */
@Component
public class ControlledGcpResourceService {

  private final JobService jobService;

  @Autowired
  public ControlledGcpResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public String createGcsBucket(
      ControlledGcsBucketResource resource,
      JobControl jobControl,
      AuthenticatedUserRequest userRequest) {
    final JobBuilder jobBuilder =
        jobService.newJob(
            resource.getDescription(),
            jobControl.getId(),
            CreateControlledResourceFlight.class,
            resource,
            userRequest);
    jobBuilder.addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, resource.getWorkspaceId());
    jobBuilder.addParameter(ControlledResourceKeys.OWNER_EMAIL, userRequest.getEmail());

    return jobBuilder.submit();
  }
}
