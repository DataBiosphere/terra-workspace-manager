package bio.terra.workspace.service.datareference;

import bio.terra.workspace.common.exception.*;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.datareference.flight.CreateDataReferenceFlight;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.datareference.model.DataReference;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for all operations on references to data.
 *
 * <p>Currently, this only supports references to uncontrolled resources. In the future, it may also
 * support references to controlled resources.
 */
@Component
public class DataReferenceService {
  private final DataReferenceDao dataReferenceDao;
  private final JobService jobService;
  private final WorkspaceService workspaceService;

  @Autowired
  public DataReferenceService(
      DataReferenceDao dataReferenceDao,
      JobService jobService,
      WorkspaceService workspaceService,
      ObjectMapper objectMapper) {
    this.dataReferenceDao = dataReferenceDao;
    this.jobService = jobService;
    this.workspaceService = workspaceService;
  }

  /**
   * Retrieve a data reference from the database by ID. References are always contained inside a
   * single workspace. Verifies workspace existence and read permission before retrieving the
   * reference.
   */
  @Traced
  public DataReference getDataReference(
      UUID workspaceId, UUID referenceId, AuthenticatedUserRequest userReq) {

    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReference(workspaceId, referenceId);
  }

  /**
   * Retrieve a data reference from the database by name. Names are unique per workspace, per data
   * reference type. Verifies workspace existence and read permission before retrieving the
   * reference.
   */
  @Traced
  public DataReference getDataReferenceByName(
      UUID workspaceId,
      DataReferenceType referenceType,
      String name,
      AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);

    return dataReferenceDao.getDataReferenceByName(workspaceId, referenceType, name);
  }

  /**
   * Create a data reference and return the newly created object. Verifies workspace existence and
   * write permission before creating the reference.
   */
  @Traced
  public DataReference createDataReference(
      DataReferenceRequest referenceRequest, AuthenticatedUserRequest userReq) {

    workspaceService.validateWorkspaceAndAction(
        userReq, referenceRequest.workspaceId(), SamConstants.SAM_WORKSPACE_WRITE_ACTION);

    String description = "Create data reference in workspace " + referenceRequest.workspaceId();

    JobBuilder createJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                CreateDataReferenceFlight.class,
                /* request = */ null,
                userReq)
            .addParameter(DataReferenceFlightMapKeys.WORKSPACE_ID, referenceRequest.workspaceId())
            .addParameter(DataReferenceFlightMapKeys.NAME, referenceRequest.name())
            .addParameter(
                DataReferenceFlightMapKeys.REFERENCE_TYPE, referenceRequest.referenceType())
            .addParameter(
                DataReferenceFlightMapKeys.CLONING_INSTRUCTIONS,
                referenceRequest.cloningInstructions())
            .addParameter(
                DataReferenceFlightMapKeys.REFERENCE_OBJECT,
                referenceRequest.referenceObject().toJson());

    UUID referenceIdResult = createJob.submitAndWait(UUID.class);

    return dataReferenceDao.getDataReference(referenceRequest.workspaceId(), referenceIdResult);
  }

  /**
   * List data references in a workspace.
   *
   * <p>References are in ascending order by reference ID. At most {@Code limit} results will be
   * returned, with the first being {@Code offset} entries from the start of the database.
   *
   * <p>Verifies workspace existence and read permission before listing the references.
   */
  @Traced
  public List<DataReference> enumerateDataReferences(
      UUID workspaceId, int offset, int limit, AuthenticatedUserRequest userReq) {
    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    return dataReferenceDao.enumerateDataReferences(workspaceId, offset, limit);
  }

  /**
   * Delete a data reference, or throw an exception if the specified reference does not exist.
   * Verifies workspace existence and write permission before deleting the reference.
   */
  @Traced
  public void deleteDataReference(
      UUID workspaceId, UUID referenceId, AuthenticatedUserRequest userReq) {

    workspaceService.validateWorkspaceAndAction(
        userReq, workspaceId, SamConstants.SAM_WORKSPACE_WRITE_ACTION);

    if (dataReferenceDao.isControlled(workspaceId, referenceId)) {
      throw new ControlledResourceNotImplementedException(
          "Unable to delete controlled resource. This functionality will be implemented in the future.");
    }

    if (!dataReferenceDao.deleteDataReference(workspaceId, referenceId)) {
      throw new DataReferenceNotFoundException("Data Reference not found.");
    }
  }
}
