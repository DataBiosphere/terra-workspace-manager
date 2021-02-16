package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.CreateControlledGoogleBucketRequestBody;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
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
      UUID workspaceId,
      CreateControlledGoogleBucketRequestBody requestBody,
      AuthenticatedUserRequest userRequest) {
    // create a job
    final String description =
        "Create controlled Google bucket named " + requestBody.getGoogleBucket().getName();
    final JobBuilder jobBuilder =
        jobService.newJob(
            description,
            requestBody.getCommon().getJobControl().getId(),
            CreateControlledGoogleBucketFlight.class,
            requestBody,
            userRequest);
    final GoogleBucketCreationParameters params = requestBody.getGoogleBucket();
    jobBuilder.addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    jobBuilder.addParameter(
        WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, userRequest.getEmail());
    jobBuilder.addParameter(GoogleBucketFlightMapKeys.BUCKET_CREATION_PARAMS.getKey(), params);
    // TODO: may not need these
    //    jobBuilder.addParameter(GoogleBucketFlightMapKeys.NAME.getKey(), params.getName());
    //    jobBuilder.addParameter(GoogleBucketFlightMapKeys.LOCATION.getKey(),
    // params.getLocation());
    //    jobBuilder.addParameter(
    //        GoogleBucketFlightMapKeys.DEFAULT_STORAGE_CLASS.getKey(),
    // params.getDefaultStorageClass());
    //    jobBuilder.addParameter(GoogleBucketFlightMapKeys.LIFECYCLE.getKey(),
    // params.getLifecycle());
    return jobBuilder.submit();
  }
}
