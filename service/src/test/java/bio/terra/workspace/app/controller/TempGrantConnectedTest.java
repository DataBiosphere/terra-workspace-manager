package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.testutils.GcsBucketTestUtils.addFileToBucket;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.testutils.GcpCloudTestUtils;
import bio.terra.workspace.common.testutils.MockMvcUtils;
import bio.terra.workspace.common.testutils.TestUtils;
import bio.terra.workspace.connected.UserAccessTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@Disabled("Only for manually testing temporary grants. Do not automate.")
@Tag("connected")
public class TempGrantConnectedTest extends BaseConnectedTest {
  private static final Logger logger = LoggerFactory.getLogger(TempGrantConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessTestUtils userAccessTestUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudTestUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogService activityLogService;
  @Autowired SamService samService;

  private UUID workspaceId;

  private boolean timeToFinish;

  @Before
  public void startup() {
    timeToFinish = false;
  }

  @After
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
  }

  @Test
  public void setupAndWaitBucket() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessTestUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    String projectId = workspace.getGcpContext().getProjectId();

    logger.info("Created workspace {} with project {}", workspaceId, projectId);

    String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
    String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");
    ApiGcpGcsBucketResource sourceBucket =
        mockMvcUtils
            .createControlledGcsBucket(
                userAccessTestUtils.defaultUserAuthRequest(),
                workspaceId,
                sourceResourceName,
                sourceBucketName,
                null,
                null,
                null)
            .getGcpBucket();
    addFileToBucket(
        userAccessTestUtils.defaultUser().getGoogleCredentials(), projectId, sourceBucketName);

    // So I can end the test and run cleanup when I'm done debugging
    while (!timeToFinish) {
      TimeUnit.MINUTES.sleep(1);
    }
  }

  @Test
  public void setupAndWaitNotebook() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessTestUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    String projectId = workspace.getGcpContext().getProjectId();

    logger.info("Created workspace {} with project {}", workspaceId, projectId);

    ApiGcpAiNotebookInstanceResource notebook =
        mockMvcUtils
            .createAiNotebookInstance(
                userAccessTestUtils.defaultUserAuthRequest(), workspaceId, null)
            .getAiNotebookInstance();

    // So I can end the test and run cleanup when I'm done debugging
    while (!timeToFinish) {
      TimeUnit.MINUTES.sleep(1);
    }
  }

  @Test
  public void setupAndWaitBigQuery() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessTestUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    String projectId = workspace.getGcpContext().getProjectId();

    logger.info("Created workspace {} with project {}", workspaceId, projectId);

    String sourceResourceName = TestUtils.appendRandomNumber("sourceresourcename");
    String sourceDatasetName = TestUtils.appendRandomNumber("sourcedatasetname");

    ApiGcpBigQueryDatasetResource resource =
        mockMvcUtils
            .createControlledBqDataset(
                userAccessTestUtils.defaultUserAuthRequest(),
                workspaceId,
                sourceResourceName,
                sourceDatasetName,
                null,
                null,
                null)
            .getBigQueryDataset();
    cloudUtils.populateBqTable(
        userAccessTestUtils.defaultUser().getGoogleCredentials(),
        resource.getAttributes().getProjectId(),
        sourceDatasetName);

    // So I can end the test and run cleanup when I'm done debugging
    while (!timeToFinish) {
      TimeUnit.MINUTES.sleep(1);
    }
  }
}
