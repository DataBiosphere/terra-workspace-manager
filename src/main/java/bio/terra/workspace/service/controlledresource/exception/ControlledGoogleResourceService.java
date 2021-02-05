package bio.terra.workspace.service.controlledresource.exception;

import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.CreateControlledGoogleBucketFlight;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGoogleResourceService {

  private JobService jobService;

  @Autowired
  public ControlledGoogleResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public UUID createBucket(
      CreateControlledGoogleBucketRequestBody body, AuthenticatedUserRequest userRequest) {
    // create a job
    final String description =
        "Create controlled Google bucket named " + body.getGoogleBucket().getName();
    final JobBuilder jobBuilder =
        jobService.newJob(
            description,
            body.getJobId(),
            CreateControlledGoogleBucketFlight.class,
            null,
            userRequest);
    return jobBuilder.submitAndWait(UUID.class, true);
  }
}
