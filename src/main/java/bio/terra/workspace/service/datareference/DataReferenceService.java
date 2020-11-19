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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper;

  @Autowired
  public DataReferenceService(
      DataReferenceDao dataReferenceDao,
      SamService samService,
      JobService jobService,
      DataReferenceValidationUtils validationUtils,
      ObjectMapper objectMapper) {
    this.dataReferenceDao = dataReferenceDao;
    this.samService = samService;
    this.jobService = jobService;
    this.validationUtils = validationUtils;
    this.objectMapper = objectMapper;
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

    validationUtils.validateReferenceObject(
        referenceRequest.referenceType(), referenceRequest.referenceObject(), userReq);
    validationUtils.validateReferenceName(referenceRequest.name());

    String description = "Create data reference in workspace " + referenceRequest.workspaceId();

    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                CreateDataReferenceFlight.class,
                null,
                userReq)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, referenceRequest.workspaceId())
            .addParameter(DataReferenceFlightMapKeys.NAME, referenceRequest.name())
            .addParameter(
                DataReferenceFlightMapKeys.REFERENCE_TYPE, referenceRequest.referenceType())
            .addParameter(
                DataReferenceFlightMapKeys.CLONING_INSTRUCTIONS,
                referenceRequest.cloningInstructions());
    try {
      createJob.addParameter(
          DataReferenceFlightMapKeys.REFERENCE_PROPERTIES,
          objectMapper.writeValueAsString(referenceRequest.referenceObject().getProperties()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Error serializing referenceObject " + referenceRequest.referenceObject().toString());
    }

    UUID referenceIdResult = createJob.submitAndWait(UUID.class, false);

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
