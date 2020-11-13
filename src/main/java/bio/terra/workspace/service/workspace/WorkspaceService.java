package bio.terra.workspace.service.workspace;

import bio.terra.workspace.common.model.Workspace;
import bio.terra.workspace.common.model.WorkspaceStage;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.DuplicateFlightIdException;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.flight.*;
import io.opencensus.contrib.spring.aop.Traced;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for workspace lifecycle operations.
 *
 * <p>This service holds core workspace management operations like creating, reading, and deleting
 * workspaces as well as their cloud contexts. New methods generally should go in new services.
 */
@Component
public class WorkspaceService {

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;
  private final SamService samService;
  private final SpendProfileService spendProfileService;

  @Autowired
  public WorkspaceService(
      JobService jobService,
      WorkspaceDao workspaceDao,
      SamService samService,
      SpendProfileService spendProfileService) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
    this.samService = samService;
    this.spendProfileService = spendProfileService;
  }

  /** Create a workspace with the specified parameters. Returns workspaceID of the new workspace. */
  @Traced
  public UUID createWorkspace(
      UUID workspaceId,
      Optional<SpendProfileId> spendProfileId,
      WorkspaceStage workspaceStage,
      String operationId,
      AuthenticatedUserRequest userReq) {

    String description = "Create workspace " + workspaceId.toString();
    JobBuilder createJob =
        jobService
            .newJob(description, operationId, WorkspaceCreateFlight.class, null, userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId);
    if (spendProfileId.isPresent()) {
      createJob.addParameter(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, spendProfileId.get().id());
    }

    createJob.addParameter(WorkspaceFlightMapKeys.WORKSPACE_STAGE, workspaceStage);

    try {
      return createJob.submitAndWait(UUID.class);
    } catch (DuplicateFlightIdException e) {
      // Indicates another request with the same operation ID already exists. Rather than running
      // multiple concurrent create flights, all flights after the first will wait for and return
      // the same result.
      jobService.waitForJob(operationId);
      return jobService.retrieveJobResult(operationId, UUID.class, userReq).getResult();
    }
  }

  /** Retrieves an existing workspace by ID */
  @Traced
  public Workspace getWorkspace(UUID id, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return workspaceDao.getWorkspace(id);
  }

  /** Delete an existing workspace by ID. Does not delete underlying cloud context. */
  @Traced
  public void deleteWorkspace(UUID id, AuthenticatedUserRequest userReq) {

    samService.workspaceAuthz(userReq, id, SamUtils.SAM_WORKSPACE_DELETE_ACTION);

    String description = "Delete workspace " + id;
    JobBuilder deleteJob =
        jobService
            .newJob(
                description,
                UUID.randomUUID().toString(),
                WorkspaceDeleteFlight.class,
                null, // Delete does not have a useful request body
                userReq)
            .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, id);
    deleteJob.submitAndWait(null);
  }

  /** Retrieves the cloud context of a workspace. */
  @Traced
  public WorkspaceCloudContext getCloudContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_READ_ACTION);
    return workspaceDao.getCloudContext(workspaceId);
  }

  /** Start a job to create a Google cloud context for the workspace. Returns the job id. */
  @Traced
  public String createGoogleContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);
    Optional<SpendProfileId> spendProfileId =
        workspaceDao.getWorkspace(workspaceId).spendProfileId();
    if (spendProfileId.isEmpty()) {
      throw new MissingSpendProfileException(workspaceId);
    }
    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId.get(), userReq);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw new NoBillingAccountException(spendProfileId.get());
    }

    String jobId = UUID.randomUUID().toString();
    jobService
        .newJob(
            "Create Google Context " + workspaceId,
            jobId,
            CreateGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .addParameter(
            WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, spendProfile.billingAccountId().get())
        .submit();
    return jobId;
  }

  /** Delete the Google cloud context for the workspace. */
  @Traced
  public void deleteGoogleContext(UUID workspaceId, AuthenticatedUserRequest userReq) {
    samService.workspaceAuthz(userReq, workspaceId, SamUtils.SAM_WORKSPACE_WRITE_ACTION);
    jobService
        .newJob(
            "Delete Google Context " + workspaceId,
            UUID.randomUUID().toString(),
            DeleteGoogleContextFlight.class,
            /* request= */ null,
            userReq)
        .addParameter(WorkspaceFlightMapKeys.WORKSPACE_ID, workspaceId)
        .submitAndWait(null);
  }
}
