package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.flight.CreateSamResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for GCP objects. */
@Component
public class ControlledGcpResourceService {

  private final JobService jobService;
  private final CrlService crlService;
  private final SamService samService;

  @Autowired
  public ControlledGcpResourceService(
      JobService jobService, CrlService crlService, SamService samService) {
    this.jobService = jobService;
    this.crlService = crlService;
    this.samService = samService;
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
    jobBuilder.addParameter(
        ControlledResourceKeys.CREATE_CLOUD_RESOURCE_STEP, new CreateGcsBucketStep(crlService));
    jobBuilder.addParameter(
        ControlledResourceKeys.CREATE_SAM_RESOURCE_STEP, new CreateSamResourceStep(samService));
    return jobBuilder.submit();
  }
}
