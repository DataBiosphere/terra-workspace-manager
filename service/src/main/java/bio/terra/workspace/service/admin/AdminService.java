package bio.terra.workspace.service.admin;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_IDS;

import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobBuilder;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.flight.cloudcontext.gcp.SyncGcpIamRolesFlight;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AdminService {

  private final JobService jobService;
  private final WorkspaceDao workspaceDao;

  public AdminService(JobService jobService, WorkspaceDao workspaceDao) {
    this.jobService = jobService;
    this.workspaceDao = workspaceDao;
  }

  public String syncIamRoleForAllGcpProjects(AuthenticatedUserRequest userRequest) {
    ArrayList<String> projectIds =
        new ArrayList<>(
            workspaceDao.listCloudContexts(CloudPlatform.GCP).stream()
                .map(cloudContext -> GcpCloudContext.deserialize(cloudContext).getGcpProjectId())
                .toList());
    JobBuilder job =
        jobService
            .newJob()
            .description("sync custom iam roles in all gcp projects")
            .jobId(UUID.randomUUID().toString())
            .flightClass(SyncGcpIamRolesFlight.class)
            .userRequest(userRequest)
            .operationType(OperationType.CREATE)
            .addParameter(GCP_PROJECT_IDS, projectIds);
    return job.submit();
  }
}
