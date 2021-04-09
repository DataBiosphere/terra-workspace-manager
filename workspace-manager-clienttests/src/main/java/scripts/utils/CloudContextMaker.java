package scripts.utils;

import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class CloudContextMaker {
  private static final Logger logger = LoggerFactory.getLogger(CloudContextMaker.class);
  private static final long CREATE_CONTEXT_POLL_SECONDS = 10;

  public static void createGcpCloudContext(UUID workspaceId, WorkspaceApi workspaceApi)
      throws Exception {
    String contextJobId = UUID.randomUUID().toString();
    var createContext =
        new CreateCloudContextRequest()
            .cloudPlatform(CloudPlatform.GCP)
            .jobControl(new JobControl().id(contextJobId));

    logger.info("Creating GCP cloud context");
    CreateCloudContextResult contextResult =
        workspaceApi.createCloudContext(createContext, workspaceId);
    while (ClientTestUtils.jobIsRunning(contextResult.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_CONTEXT_POLL_SECONDS);
      contextResult = workspaceApi.getCreateCloudContextResult(workspaceId, contextJobId);
    }
    logger.info(
        "Create GCP context status is {}", contextResult.getJobReport().getStatus());
    assertThat(contextResult.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
  }

  public static void deleteGcpCloudContext(UUID workspaceId, WorkspaceApi workspaceApi)
      throws Exception {
    // Delete the cloud context. This is not required. Just some exercise for deleteCloudContext
    logger.info("Deleting the cloud context");
    workspaceApi.deleteCloudContext(workspaceId, CloudPlatform.GCP);
  }
}
