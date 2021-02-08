package bio.terra.workspace.service.controlledresource;

import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.CreateControlledGoogleBucketFlight;
import bio.terra.workspace.service.workspace.flight.GoogleBucketFlightMapKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ControlledGoogleResourceService {

  private JobService jobService;

  @Autowired
  public ControlledGoogleResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public String createBucket(
      CreateControlledGoogleBucketRequestBody requestBody, AuthenticatedUserRequest userRequest) {
    // create a job
    final String description =
        "Create controlled Google bucket named " + requestBody.getGoogleBucket().getName();
    final JobBuilder jobBuilder =
        jobService.newJob(
            description,
            requestBody.getJobId(),
            CreateControlledGoogleBucketFlight.class,
            requestBody,
            userRequest);
    final GoogleBucketCreationParameters params = requestBody.getGoogleBucket();
    jobBuilder.addParameter(GoogleBucketFlightMapKeys.NAME.getKey(), params.getName());
    jobBuilder.addParameter(GoogleBucketFlightMapKeys.LOCATION.getKey(), params.getLocation());
    jobBuilder.addParameter(
        GoogleBucketFlightMapKeys.DEFAULT_STORAGE_CLASS.getKey(), params.getDefaultStorageClass());
    jobBuilder.addParameter(GoogleBucketFlightMapKeys.LIFECYCLE.getKey(), params.getLifecycle());
    return jobBuilder.submit(false);
  }

  //  public CreatedControlledGoogleBucket getCreateBucketResult(String jobId) {
  //
  //  }
}
