package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.common.exception.ControlledResourceNotFoundException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
import bio.terra.workspace.service.resource.controlled.flight.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for GCP objects. */
@Component
public class ControlledGcpResourceService {

  private final JobService jobService;
  private final ControlledResourceDao controlledResourceDao;

  @Autowired
  public ControlledGcpResourceService(JobService jobService,
      ControlledResourceDao controlledResourceDao) {
    this.jobService = jobService;
    this.controlledResourceDao = controlledResourceDao;
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

  /**
   * Retrieve the bucket metadata from the DAO(s)
   * @param workspaceId - workspace containing this bucket
   * @param resourceId - resource ID for the bucket
   * @return attributes we store about this bucket (i.e. its name)
   */
  public GoogleBucketStoredAttributes getBucket(UUID workspaceId, UUID resourceId) {
    final ControlledResourceDbModel model = controlledResourceDao.getControlledResource(resourceId)
        .orElseThrow(() -> new ControlledResourceNotFoundException(String.format("Resource with ID %s not found", resourceId.toString())));
    return ControlledGcsBucketResource.attributesToOutputApiModel(model.getAttributes());
  }
}
