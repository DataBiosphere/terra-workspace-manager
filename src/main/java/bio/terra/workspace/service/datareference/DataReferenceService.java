package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceList;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.datareference.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.datareference.exception.InvalidDataReferenceException;
import bio.terra.workspace.service.datareference.flight.CreateDataReferenceFlight;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import io.opencensus.contrib.spring.aop.Traced;
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

  @Traced
  public DataReferenceDescription getDataReference(
      UUID workspaceId, UUID referenceId, AuthenticatedUserRequest userReq) {

    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReference(workspaceId, referenceId);
  }

  @Traced
  public DataReferenceDescription getDataReferenceByName(
      UUID workspaceId,
      ReferenceTypeEnum referenceType,
      String name,
      AuthenticatedUserRequest userReq) {

    validationUtils.validateReferenceName(name);
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReferenceByName(workspaceId, referenceType, name);
  }

  @Traced
  public DataReferenceDescription createDataReference(
      UUID workspaceId, CreateDataReferenceRequestBody body, AuthenticatedUserRequest userReq) {

    // validate shape of request as soon as it comes in
    if (body.getResourceId() != null) {
      throw new ControlledResourceNotImplementedException(
          "Unable to create a reference with a resourceId, use a reference type and description"
              + " instead. This functionality will be implemented in the future.");
    }
    if (body.getReferenceType() == null || body.getReference() == null) {
      throw new InvalidDataReferenceException(
          "Data reference must contain a reference type and a reference description");
    }
    // TODO: remove this check when we add support for resource-specific credentials.
    if (body.getCredentialId() != null) {
      throw new InvalidDataReferenceException(
          "Resource-specific credentials are not supported yet.");
    }

    validationUtils.validateReferenceName(body.getName());
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);

    String description = "Create data reference in workspace " + workspaceId;

    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                CreateDataReferenceFlight.class,
                body,
                userReq)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, workspaceId);

    DataRepoSnapshot ref =
        validationUtils.validateReference(body.getReferenceType(), body.getReference(), userReq);
    createJob.addParameter(DataReferenceFlightMapKeys.REFERENCE, ref);

    UUID referenceIdResult = createJob.submitAndWait(UUID.class);

    return dataReferenceDao.getDataReference(workspaceId, referenceIdResult);
  }

  @Traced
  public DataReferenceList enumerateDataReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return dataReferenceDao.enumerateDataReferences(
        workspaceId, userReq.getReqId().toString(), offset, limit);
  }

  @Traced
  public void deleteDataReference(
      UUID workspaceId, UUID referenceId, AuthenticatedUserRequest userReq) {

    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);

    if (dataReferenceDao.isControlled(workspaceId, referenceId)) {
      throw new ControlledResourceNotImplementedException(
          "Unable to delete controlled resource. This functionality will be implemented in the future.");
    }

    if (!dataReferenceDao.deleteDataReference(workspaceId, referenceId)) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }
}
