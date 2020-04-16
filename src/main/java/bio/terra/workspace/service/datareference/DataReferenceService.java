package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.common.exception.WorkspaceNotFoundException;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.JobControl;
import bio.terra.workspace.service.datareference.flight.DataReferenceCreateFlight;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceService {

  private JobService jobService;
  private SamService samService;
  private DataReferenceDao dataReferenceDao;
  private WorkspaceDao workspaceDao;

  @Autowired
  public DataReferenceService(
      JobService jobService,
      SamService samService,
      DataReferenceDao dataReferenceDao,
      WorkspaceDao workspaceDao) {
    this.jobService = jobService;
    this.samService = samService;
    this.dataReferenceDao = dataReferenceDao;
    this.workspaceDao = workspaceDao;
  }

  public void createDataReference(
      String id, CreateDataReferenceRequestBody body, AuthenticatedUserRequest userReq) {

    if (!samService.isAuthorized(
        userReq.getRequiredToken(),
        SamUtils.SAM_WORKSPACE_RESOURCE,
        id,
        SamUtils.SAM_WORKSPACE_WRITE_ACTION)) {
      throw new SamUnauthorizedException(
          "User " + userReq.getEmail() + " is not an editor in workspace " + id);
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

  public DataReferenceList enumerateDataReferences(
      String workspaceId,
      int offset,
      int limit,
      String filterControlled,
      AuthenticatedUserRequest userReq) {
    // Service steps:
    // First, validate that user has read access on this workspace
    // Next, validate that the workspace exists.
    // Finally, return result of calling the DAO.
    // I don't think we need to do per-reference checks because individual data references don't
    // store
    // user permission in workspaces (right?).
    // SQL should make sure it's not showing invisible resources.
    if (!samService.isAuthorized(
        userReq.getRequiredToken(),
        SamUtils.SAM_WORKSPACE_RESOURCE,
        workspaceId,
        SamUtils.SAM_WORKSPACE_READ_ACTION)) {
      throw new SamUnauthorizedException(
          "User " + userReq.getEmail() + " is not authorized to read workspace " + workspaceId);
    }
    // TODO: do we want to give users a different error when the specified workspace doesn't exist?
    //  It requires an additional DB lookup (as an empty enumerate doesn't tell you whether
    //  or not a workspace exists), but it's a nicer user experience for catching small mistakes.
    if (!workspaceDao.workspaceExists(workspaceId)) {
      throw new WorkspaceNotFoundException("No workspace exists with ID " + workspaceId);
    }

    return dataReferenceDao.enumerateDataReferences(
        workspaceId, userReq.getReqId().toString(), offset, limit, filterControlled);
  }
}
