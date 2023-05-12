package bio.terra.workspace.service.admin;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IS_WET_RUN;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WORKSPACE_ID_TO_GCP_PROJECT_ID_MAP;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AdminService {
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;

  public AdminService(JobService jobService, WorkspaceDao workspaceDao) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
  }

  @Nullable
  public String syncIamRoleForAllGcpProjects(AuthenticatedUserRequest userRequest, boolean wetRun) {
    Map<UUID, String> workspaceIdToGcpProjectIdMap = new HashMap<>();
    for (Map.Entry<UUID, DbCloudContext> cloudContext :
        workspaceDao.getWorkspaceIdToGcpCloudContextMap().entrySet()) {
      // GcpCloudContext.deserialize will only return empty for contexts in the CREATING state,
      // which are filtered out above in getWorkspaceIdToGcpCloudContextMap.
      workspaceIdToGcpProjectIdMap.put(
          cloudContext.getKey(),
          GcpCloudContext.deserialize(cloudContext.getValue()).orElseThrow().getGcpProjectId());
    }

    if (workspaceIdToGcpProjectIdMap.isEmpty()) {
      throw new InternalServerErrorException("No GCP projects found");
    }
    JobBuilder job =
        jobService
            .newJob()
            .description("sync custom iam roles in all active gcp projects")
            .jobId(UUID.randomUUID().toString())
            .flightClass(SyncGcpIamRolesFlight.class)
            .userRequest(userRequest)
            .operationType(OperationType.ADMIN_UPDATE)
            .addParameter(WORKSPACE_ID_TO_GCP_PROJECT_ID_MAP, workspaceIdToGcpProjectIdMap)
            .addParameter(IS_WET_RUN, wetRun);
    return job.submit();
  }
}
