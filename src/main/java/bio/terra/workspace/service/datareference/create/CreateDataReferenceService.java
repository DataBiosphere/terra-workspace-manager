package bio.terra.workspace.service.datareference.create;

import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.datareference.create.flight.DataReferenceCreateFlight;
import bio.terra.workspace.service.datareference.create.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateDataReferenceService {

  private JobService jobService;
  private SamService samService;

  @Autowired
  public CreateDataReferenceService(JobService jobService, SamService samService) {
    this.jobService = jobService;
    this.samService = samService;
  }

  public void createDataReference(
      String id, CreateDataReferenceRequestBody body, AuthenticatedUserRequest userReq) {

    try {
      samService.isAuthorized(
          userReq.getRequiredToken(),
          SamUtils.SAM_WORKSPACE_RESOURCE,
          id,
          SamUtils.SAM_WORKSPACE_WRITE_ACTION);
    } catch (ApiException samEx) {
      throw new SamApiException(samEx);
    }

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
            .addParameter(
                DataReferenceFlightMapKeys.CLONING_INSTRUCTIONS, body.getCloningInstructions())
            .addParameter(DataReferenceFlightMapKeys.CREDENTIAL_ID, body.getCredentialId())
            .addParameter(DataReferenceFlightMapKeys.REFERENCE, body.getReference());
    createJob.submit();
  }
}
