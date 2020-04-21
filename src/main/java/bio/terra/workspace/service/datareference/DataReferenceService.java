package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.service.datareference.flight.*;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceService {
  private final DataReferenceDao dataReferenceDao;
  private final SamService samService;
  private final JobService jobService;

  @Autowired
  public DataReferenceService(
      DataReferenceDao dataReferenceDao, SamService samService, JobService jobService) {
    this.dataReferenceDao = dataReferenceDao;
    this.samService = samService;
    this.jobService = jobService;
  }

  public DataReferenceDescription getDataReference(
      String workspaceId, String referenceId, AuthenticatedUserRequest userReq) {

    try {
      samService.isAuthorized(
          userReq.getRequiredToken(),
          SamUtils.SAM_WORKSPACE_RESOURCE,
          workspaceId,
          SamUtils.SAM_WORKSPACE_READ_ACTION);
    } catch (ApiException samEx) {
      throw new SamApiException(samEx);
    }

    return dataReferenceDao.getDataReference(UUID.fromString(referenceId));
  }

  public DataReferenceDescription createDataReference(
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

    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                CreateDataReferenceFlight.class,
                body,
                userReq)
            .addParameter(DataReferenceFlightMapKeys.REFERENCE_ID, referenceId)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, id)
            .addParameter(DataReferenceFlightMapKeys.NAME, body.getName())
            .addParameter(DataReferenceFlightMapKeys.REFERENCE_TYPE, body.getReferenceType())
            .addParameter(
                DataReferenceFlightMapKeys.CLONING_INSTRUCTIONS, body.getCloningInstructions())
            .addParameter(DataReferenceFlightMapKeys.CREDENTIAL_ID, body.getCredentialId())
            .addParameter(DataReferenceFlightMapKeys.REFERENCE, body.getReference());

    return createJob.submitAndWait(DataReferenceDescription.class);
  }
}
