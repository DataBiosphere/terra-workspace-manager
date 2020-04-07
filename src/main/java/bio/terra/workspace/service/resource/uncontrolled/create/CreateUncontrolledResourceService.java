package bio.terra.workspace.service.resource.uncontrolled.create;

import bio.terra.workspace.generated.model.CreateWorkspaceDataReferenceRequestBody;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.uncontrolled.create.flight.UncontrolledResourceCreateFlight;
import bio.terra.workspace.service.resource.uncontrolled.create.flight.UncontrolledResourceFlightMapKeys;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateUncontrolledResourceService {

  private JobService jobService;

  @Autowired
  public CreateUncontrolledResourceService(JobService jobService) {
    this.jobService = jobService;
  }

  public void createDataReference(
      String id, CreateWorkspaceDataReferenceRequestBody body, AuthenticatedUserRequest userReq) {

    // TODO: check sam for WRITE action

    UUID referenceId = UUID.randomUUID();
    String description = "Create data reference " + referenceId.toString() + " in workspace " + id;
    JobControl jobControl = body.getJobControl();

    // TODO: Need to support pubsub notifications here.
    JobBuilder createJob =
        jobService
            .newJob(
                description,
                jobControl.getJobid(),
                UncontrolledResourceCreateFlight.class,
                body,
                userReq)
            .addParameter(UncontrolledResourceFlightMapKeys.REFERENCE_ID, referenceId)
            .addParameter(UncontrolledResourceFlightMapKeys.WORKSPACE_ID, id)
            .addParameter(UncontrolledResourceFlightMapKeys.NAME, body.getName())
            .addParameter(UncontrolledResourceFlightMapKeys.REFERENCE_TYPE, body.getReferenceType())
            .addParameter(UncontrolledResourceFlightMapKeys.REFERENCE, body.getReference());
    createJob.submit();
  }
}
