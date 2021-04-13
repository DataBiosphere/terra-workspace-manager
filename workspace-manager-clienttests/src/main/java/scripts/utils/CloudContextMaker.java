package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for creating/deleting cloud contexts for client tests */
public class CloudContextMaker {
  private static final Logger logger = LoggerFactory.getLogger(CloudContextMaker.class);
  private static final Duration CREATE_CONTEXT_POLL_INTERVAL = Duration.ofSeconds(10);

  private CloudContextMaker() {}

  /** Creates a GCP cloud context for a given workspace. Returns the GCP project ID as a string. */
  public static String createGcpCloudContext(UUID workspaceId, WorkspaceApi workspaceApi)
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
      Thread.sleep(CREATE_CONTEXT_POLL_INTERVAL.toMillis());
      contextResult = workspaceApi.getCreateCloudContextResult(workspaceId, contextJobId);
    }
    logger.info(
        "Create GCP context status is {}", contextResult.getJobReport().getStatus().toString());
    assertThat(contextResult.getJobReport().getStatus(), equalTo(JobReport.StatusEnum.SUCCEEDED));
    return contextResult.getGcpContext().getProjectId();
  }

  /**
   * Deletes the GCP cloud context on a given workspace. Cloud context deletion will happen
   * automatically as part of workspace deletion, but can also be executed separately here.
   */
  public static void deleteGcpCloudContext(UUID workspaceId, WorkspaceApi workspaceApi)
      throws Exception {
    logger.info("Deleting the cloud context");
    workspaceApi.deleteCloudContext(workspaceId, CloudPlatform.GCP);
  }
}
