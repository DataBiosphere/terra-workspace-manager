package bio.terra.workspace.service.admin;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_IDS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IS_WET_RUN;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.admin.flights.cloudcontexts.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.ArrayList;
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
    ArrayList<String> projectIds =
        new ArrayList<>(
            workspaceDao.listCloudContexts(CloudPlatform.GCP).stream()
                .map(cloudContext -> GcpCloudContext.deserialize(cloudContext).getGcpProjectId())
                .toList());
    if (projectIds.isEmpty()) {
      throw new InternalServerErrorException("No GCP projects found");
    }
    JobBuilder job =
        jobService
            .newJob()
            .description("sync custom iam roles in all gcp projects")
            .jobId(UUID.randomUUID().toString())
            .flightClass(SyncGcpIamRolesFlight.class)
            .userRequest(userRequest)
            .operationType(OperationType.CREATE)
            .addParameter(GCP_PROJECT_IDS, projectIds)
            .addParameter(IS_WET_RUN, wetRun);
    return job.submit();
  }
}
