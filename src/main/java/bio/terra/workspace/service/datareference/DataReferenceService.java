package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.service.datareference.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.flight.*;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataReferenceService {
  private final DataReferenceDao dataReferenceDao;
  private final SamService samService;
  private final JobService jobService;
  private final DataReferenceValidationUtils validationUtils;

  @Autowired
  public DataReferenceService(
      DataReferenceDao dataReferenceDao,
      SamService samService,
      JobService jobService,
      DataReferenceValidationUtils validationUtils) {
    this.dataReferenceDao = dataReferenceDao;
    this.samService = samService;
    this.jobService = jobService;
    this.validationUtils = validationUtils;
  }

  public DataReferenceDescription getDataReference(
      String workspaceId, String referenceId, AuthenticatedUserRequest userReq) {

    authz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReference(UUID.fromString(referenceId));
  }

  public DataReferenceDescription createDataReference(
      String workspaceId, CreateDataReferenceRequestBody body, AuthenticatedUserRequest userReq) {

    // validate shape of request as soon as it comes in
    if ((body.getReferenceType().isPresent() && body.getReference().isPresent())
        == body.getResourceId().isPresent()) {
      throw new InvalidDataReferenceException(
          "Data reference must contain either a resource id or a reference type and a reference description");
    }

    authz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);

    UUID referenceId = UUID.randomUUID();
    String description =
        "Create data reference " + referenceId.toString() + " in workspace " + workspaceId;

    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                CreateDataReferenceFlight.class,
                body,
                userReq)
            .addParameter(DataReferenceFlightMapKeys.REFERENCE_ID, referenceId)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, UUID.fromString(workspaceId));

    if (body.getReferenceType().isPresent() && body.getReference().isPresent()) {
      String ref =
          validationUtils.validateReference(
              DataReferenceDescription.ReferenceTypeEnum.fromValue(body.getReferenceType().get()),
              body.getReference().get(),
              userReq);
      createJob.addParameter(DataReferenceFlightMapKeys.REFERENCE, ref);
    }

    createJob.submitAndWait(String.class);

    return dataReferenceDao.getDataReference(referenceId);
  }

  public DataReferenceList enumerateDataReferences(
      String workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    authz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return dataReferenceDao.enumerateDataReferences(
        workspaceId, userReq.getReqId().toString(), offset, limit);
  }

  public void deleteDataReference(
      String workspaceId, String referenceId, AuthenticatedUserRequest userReq) {

    authz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);

    if (dataReferenceDao.isControlled(UUID.fromString(referenceId))) {
      throw new ControlledResourceNotImplementedException(
          "Unable to delete controlled resource. This functionality will be implemented in the future.");
    }

    if (!dataReferenceDao.deleteDataReference(UUID.fromString(referenceId))) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }

  private void authz(AuthenticatedUserRequest userReq, String workspaceId, String action) {
    boolean isAuthorized =
        samService.isAuthorized(
            userReq.getRequiredToken(), SamUtils.SAM_WORKSPACE_RESOURCE, workspaceId, action);
    if (!isAuthorized)
      throw new SamUnauthorizedException(
          "User "
              + userReq.getEmail()
              + " is not authorized to "
              + action
              + " workspace "
              + workspaceId
              + " or it doesn't exist.");
  }
}
