package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.IamRole;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CRUD methods for controlled objects. */
@Component
public class ControlledResourceService {

  private final JobService jobService;
  private final WorkspaceService workspaceService;
  private final ResourceDao resourceDao;

  @Autowired
  public ControlledResourceService(
      JobService jobService, WorkspaceService workspaceService, ResourceDao resourceDao) {
    this.jobService = jobService;
    this.workspaceService = workspaceService;
    this.resourceDao = resourceDao;
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

  public ControlledResource getControlledResource(
      UUID workspaceId, UUID resourceId, AuthenticatedUserRequest userReq) {
    // TODO: Fix this based on resolution of permission model issue.
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
    return castControlledResource(wsmResource);
  }

  private ControlledResource castControlledResource(WsmResource wsmResource) {
    if (!(wsmResource instanceof ControlledResource)) {
      throw new InvalidMetadataException("Returned resource is not a controlled resource");
    }
    return (ControlledResource) wsmResource;
  }
}
