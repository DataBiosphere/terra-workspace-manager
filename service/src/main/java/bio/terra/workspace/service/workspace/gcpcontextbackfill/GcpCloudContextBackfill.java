package bio.terra.workspace.service.workspace.gcpcontextbackfill;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.flight.backfill.UpdateControlledBigQueryDatasetsLifetimeFlight;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.OperationType;
import io.opencensus.contrib.spring.aop.Traced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// TODO: PF-2694 - remove backfill once propagated to all environments
@Component
public class GcpCloudContextBackfill {
  private static final Logger logger = LoggerFactory.getLogger(GcpCloudContextBackfill.class);
  private final SamService samService;
  private final JobService jobService;
  private final WorkspaceDao workspaceDao;

  @Autowired
  public GcpCloudContextBackfill(
    SamService samService,
    JobService jobService,
    WorkspaceDao workspaceDao) {
    this.samService = samService;
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
  }

  /**
   * Starts a flight to update missing lifetime for controlled BigQuery datasets.
   *
   * @return the job ID string (to await job completion in the connected tests.)
   */
  @Traced
  public @Nullable String gcpCloudContextBackfillAsync() {
    String wsmSaToken = samService.getWsmServiceAccountToken();
    // wsmSaToken is null for unit test when samService is mocked out.
    if (wsmSaToken == null) {
      logger.warn("Not running GCP context backfill because workspace manager service account token is null");
      return null;
    }

    List<String> backfillIds = workspaceDao.getGcpContextBackfillWorkspaceList();
    if (backfillIds.isEmpty()) {
      logger.info("No GCP cloud contexts to backfill");
      return null;
    }

    AuthenticatedUserRequest wsmSaRequest =
      new AuthenticatedUserRequest().token(Optional.of(wsmSaToken));
    return jobService
      .newJob()
      .description("Backfill Sam sync'd group names in GCP cloud context")
      .flightClass(GcpContextBackfillFlight.class)
      .userRequest(wsmSaRequest)
      .operationType(OperationType.UPDATE)
      // Overloading the key, but only for temporary back-fill
      .addParameter(WorkspaceFlightMapKeys.UPDATED_WORKSPACES, backfillIds)
      .submit();
  }
}
