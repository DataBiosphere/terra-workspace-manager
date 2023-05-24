package bio.terra.workspace.service.workspace;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsPolicyPair;
import bio.terra.workspace.common.BaseUnitTestMockDataRepoService;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.MvcWorkspaceApi;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiCloneResourceResult;
import bio.terra.workspace.generated.model.ApiCloneWorkspaceResult;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiProperty;
import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInput;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.generated.model.ApiWsmPolicyPair;
import bio.terra.workspace.generated.model.ApiWsmPolicyUpdateResult;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiConversionUtils;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import bio.terra.workspace.service.workspace.model.WorkspaceDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.DEFAULT_SPEND_PROFILE;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.SHORT_DESCRIPTION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.TYPE_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.VERSION_PROPERTY;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.WORKSPACE_NAME;
import static bio.terra.workspace.common.fixtures.WorkspaceFixtures.getUserFacingId;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test the WorkspaceV2 endpoints in isolation
public class WorkspaceV2ApiTest extends BaseUnitTestMockDataRepoService {
  /** A fake group-constraint policy for a workspace. */
  private static final TpsPolicyInput TPS_GROUP_POLICY =
      new TpsPolicyInput()
          .namespace("terra")
          .name("group-constraint")
          .addAdditionalDataItem(new TpsPolicyPair().key("group").value("my_fake_group"));

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MvcWorkspaceApi mvcWorkspaceApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired WorkspaceActivityLogService workspaceActivityLogService;
  @Autowired JobService jobService;
  @Autowired WorkspaceDao workspaceDao;

  private static TpsPaoGetResult emptyWorkspacePao() {
    return new TpsPaoGetResult()
        .component(TpsComponent.WSM)
        .objectType(TpsObjectType.WORKSPACE)
        .objectId(UUID.randomUUID())
        .sourcesObjectIds(Collections.emptyList())
        .attributes(new TpsPolicyInputs())
        .effectiveAttributes(new TpsPolicyInputs());
  }

  @BeforeEach
  public void setup() throws InterruptedException {
    when(mockSamService()
            .isAuthorized(
                any(), eq(SamResource.SPEND_PROFILE), any(), eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    when(mockSamService().listRequesterRoles(any(), any(), any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
    when(mockSamService().getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(any()))
        .thenReturn(USER_REQUEST.getEmail());
    when(mockSamService().isAuthorized(any(), eq(SamResource.WORKSPACE), any(), any()))
        .thenReturn(true);

    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    // We don't need to mock tpsCheck() because Mockito will already do nothing by default.
  }

  @Test
  public void testAsyncDeleteWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithoutCloudContext(USER_REQUEST);
    mvcWorkspaceApi.deleteWorkspaceAndWait(USER_REQUEST, workspace.getId());
  }

  @Test
  public void testAsyncDeleteGcpCloudContext() throws Exception {
    ApiCreatedWorkspace workspace = mockMvcUtils.createWorkspaceWithCloudContext(USER_REQUEST);
    mvcWorkspaceApi.deleteCloudContextAndWait(USER_REQUEST, workspace.getId(), CloudPlatform.GCP);
    mvcWorkspaceApi.deleteWorkspaceAndWait(USER_REQUEST, workspace.getId());
  }

}
