package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.CreateControlledGoogleBucketFlight;
import bio.terra.workspace.service.workspace.flight.GoogleBucketFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGoogleResourceService {

  private final JobService jobService;

  @Autowired
  public ControlledGoogleResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public String createBucket(
      ControlledGcsBucketResource resource,
      JobControl jobControl,
      AuthenticatedUserRequest userRequest) {
   final JobBuilder jobBuilder =
        jobService.newJob(
            resource.getDescription(),
            jobControl.getId(),
            CreateControlledGoogleBucketFlight.class,
            resource,
            userRequest);
    jobBuilder.addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, resource.getWorkspaceId());
    jobBuilder.addParameter(
        WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, userRequest.getEmail());
    return jobBuilder.submit();
  }
}
