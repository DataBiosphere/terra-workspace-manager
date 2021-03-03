package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.IamRole;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for controlled objects. */
@Component
public class ControlledResourceService {

  private final JobService jobService;

  @Autowired
  public ControlledResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public String createGcsBucket(
      ControlledGcsBucketResource resource,
      GoogleBucketCreationParameters creationParameters,
      IamRole creationIamRole,
      JobControl jobControl,
      AuthenticatedUserRequest userRequest) {

    final String jobDescription =
        String.format(
            "Create controlled resource %s; id %s; name %s",
            resource.getResourceType(), resource.getResourceId(), resource.getName());

    final JobBuilder jobBuilder =
        jobService
            .newJob(
                jobDescription,
                jobControl.getId(),
                CreateControlledResourceFlight.class,
                resource,
                userRequest)
            .addParameter(ControlledResourceKeys.CREATION_PARAMETERS, creationParameters)
            .addParameter(ControlledResourceKeys.IAM_ROLE, creationIamRole);

    return jobBuilder.submit();
  }
}
