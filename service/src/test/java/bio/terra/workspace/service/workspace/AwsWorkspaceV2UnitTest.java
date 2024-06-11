package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SAM_USER;
import static bio.terra.workspace.common.mocks.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_ENVIRONMENT;
import static bio.terra.workspace.common.utils.AwsTestUtils.AWS_METADATA;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import bio.terra.aws.resource.discovery.EnvironmentDiscovery;
import bio.terra.workspace.common.BaseAwsSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.common.utils.AwsTestUtils;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

public class AwsWorkspaceV2UnitTest extends BaseAwsSpringBootUnitTest {

  @Autowired JobService jobService;
  @Autowired private AwsCloudContextService awsCloudContextService;
  @Autowired WorkspaceService workspaceService;
  @Mock private EnvironmentDiscovery mockEnvironmentDiscovery;

  @Test
  void createDeleteWorkspaceV2WithContextTest() throws Exception {
    try (MockedStatic<AwsUtils> mockAwsUtils =
        mockStatic(AwsUtils.class, Mockito.CALLS_REAL_METHODS)) {

      when(mockSamService().getSamUser((AuthenticatedUserRequest) any())).thenReturn(SAM_USER);
      when(mockSamService().getUserStatusInfo(any()))
          .thenReturn(
              new UserStatusInfo()
                  .userEmail(SAM_USER.getEmail())
                  .userSubjectId(SAM_USER.getSubjectId()));

      when(mockEnvironmentDiscovery.discoverEnvironment()).thenReturn(AWS_ENVIRONMENT);
      mockAwsUtils
          .when(() -> AwsUtils.createEnvironmentDiscovery(any()))
          .thenReturn(mockEnvironmentDiscovery);

      when(awsCloudContextService.discoverEnvironment(SAM_USER.getEmail()))
          .thenReturn(AWS_ENVIRONMENT);

      // create workspace (with cloud context)
      Workspace workspace = WorkspaceFixtures.createDefaultMcWorkspace();
      UUID workspaceUuid = workspace.workspaceId();
      String jobId = UUID.randomUUID().toString();
      workspaceService.createWorkspaceV2(
          workspace,
          null,
          null,
          CloudPlatform.AWS,
              DEFAULT_GCP_SPEND_PROFILE,
          null,
          jobId,
          USER_REQUEST);
      jobService.waitForJob(jobId);

      // cloud context should have been created
      assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isPresent());
      AwsCloudContext createdCloudContext =
          awsCloudContextService.getAwsCloudContext(workspaceUuid).get();
      AwsTestUtils.assertAwsCloudContextFields(
          AWS_METADATA, createdCloudContext.getContextFields());
      AwsTestUtils.assertCloudContextCommonFields(
          createdCloudContext.getCommonFields(),
          WorkspaceFixtures.DEFAULT_GCP_SPEND_PROFILE_ID,
          WsmResourceState.READY,
          null);

      // delete workspace (with cloud context)
      jobId = UUID.randomUUID().toString();
      workspaceService.deleteWorkspaceAsync(workspace, USER_REQUEST, jobId, "result-path");
      jobService.waitForJob(jobId);

      // cloud context should have been deleted
      assertTrue(awsCloudContextService.getAwsCloudContext(workspaceUuid).isEmpty());
    }
  }
}
