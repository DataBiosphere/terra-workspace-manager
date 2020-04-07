package bio.terra.workspace.service.datareference.create;

import bio.terra.workspace.generated.model.CreateWorkspaceDataReferenceRequestBody;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.datareference.create.flight.DataReferenceCreateFlight;
import bio.terra.workspace.service.datareference.create.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateDataReferenceService {

  private JobService jobService;

  @Autowired
  public CreateDataReferenceService(JobService jobService) {
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
                description, jobControl.getJobid(), DataReferenceCreateFlight.class, body, userReq)
            .addParameter(DataReferenceFlightMapKeys.REFERENCE_ID, referenceId)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, id)
            .addParameter(DataReferenceFlightMapKeys.NAME, body.getName())
            .addParameter(DataReferenceFlightMapKeys.REFERENCE_TYPE, body.getReferenceType())
            .addParameter(DataReferenceFlightMapKeys.REFERENCE, body.getReference());
    createJob.submit();
  }
}
