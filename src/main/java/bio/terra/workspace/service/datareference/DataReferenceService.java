package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.datareference.flight.CreateDataReferenceFlight;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.utils.DataReferenceValidationUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
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
  public DataReference getDataReference(
      UUID workspaceId, UUID referenceId, AuthenticatedUserRequest userReq) {

    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReference(workspaceId, referenceId);
  }

  @Traced
  public DataReference getDataReferenceByName(
      UUID workspaceId,
      DataReferenceType referenceType,
      String name,
      AuthenticatedUserRequest userReq) {

    validationUtils.validateReferenceName(name);
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReferenceByName(workspaceId, referenceType, name);
  }

  @Traced
  public DataReference createDataReference(
      DataReferenceRequest referenceRequest, AuthenticatedUserRequest userReq) {

    samService.workspaceAuthz(
        userReq, referenceRequest.workspaceId(), SamUtils.SAM_WORKSPACE_WRITE_ACTION);

    String description = "Create data reference in workspace " + referenceRequest.workspaceId();

    JobBuilder createJob =
        jobService.newJob(
            description,
            UUID.randomUUID().toString(),
            CreateDataReferenceFlight.class,
            null,
            userReq);

    validationUtils.validateReferenceObject(
        referenceRequest.referenceType(), referenceRequest.referenceObject(), userReq);
    createJob.addParameter(DataReferenceFlightMapKeys.REFERENCE, referenceRequest);

    UUID referenceIdResult = createJob.submitAndWait(UUID.class);

    return dataReferenceDao.getDataReference(referenceRequest.workspaceId(), referenceIdResult);
  }

  @Traced
  public List<DataReference> enumerateDataReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return dataReferenceDao.enumerateDataReferences(workspaceId, offset, limit);
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
