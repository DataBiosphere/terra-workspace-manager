package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.JobsApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

/** Tests of the JobsAPI */
public class Jobs extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(Jobs.class);

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);

    // TODO: when we implement list we will want the second user to test for job
    //  visibility, so leave the setup here.
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    JobsApi jobsApi = ClientTestUtils.getJobsClient(testUser, server);

    // The purpose of this test is to exercise the jobsApi so we
    // create a cloud context - something that will run async
    String contextJobId = UUID.randomUUID().toString();
    var createContext =
        new CreateCloudContextRequest()
            .cloudPlatform(CloudPlatform.GCP)
            .jobControl(new JobControl().id(contextJobId));

    logger.info("Creating GCP cloud context");
    CreateCloudContextResult contextResult =
        workspaceApi.createCloudContext(createContext, getWorkspaceId());
    JobReport jobReport = contextResult.getJobReport();

    while (ClientTestUtils.jobIsRunning(jobReport)) {
      TimeUnit.SECONDS.sleep(10);
      jobReport = jobsApi.retrieveJob(contextJobId);
    }
    logger.info("Create GCP context status is {}", jobReport.getStatus().toString());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
  }
}
