package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.testutils.MockMvcUtils.assertApiFlexibleResourceEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.testfixtures.PolicyFixtures;
import bio.terra.workspace.common.testutils.GcpCloudTestUtils;
import bio.terra.workspace.common.testutils.MockMvcUtils;
import bio.terra.workspace.common.testutils.StairwayTestUtils;
import bio.terra.workspace.common.testutils.TestUtils;
import bio.terra.workspace.connected.UserAccessTestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@Tag("connectedPlus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ControlledFlexibleResourceApiControllerConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledFlexibleResourceApiControllerConnectedTest.class);

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
  private UUID workspaceId2;
  private ApiFlexibleResource sourceFlexResource;

  private final String sourceResourceName =
      TestUtils.appendRandomNumber("source-flexible-resource-name");
  private static final String sourceTypeNamespace = "terra";
  private static final String sourceType = "fake-flexible-type";
  private static final String sourceData = "{\"name\":\"original JSON\"}";

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithoutCloudContext(userAccessTestUtils.defaultUserAuthRequest())
            .getId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithPolicy(
                userAccessTestUtils.defaultUserAuthRequest(),
                new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.GROUP_POLICY_DEFAULT))
            .getId();
    // Source flex resource used in clone tests.
    sourceFlexResource =
        mockMvcUtils
            .createFlexibleResource(
                userAccessTestUtils.defaultUserAuthRequest(),
                workspaceId,
                sourceResourceName,
                sourceTypeNamespace,
                sourceType,
                ControlledFlexibleResource.getEncodedJSONFromString(sourceData))
            .getFlexibleResource();
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId, userAccessTestUtils.defaultUserAuthRequest());
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId2, userAccessTestUtils.defaultUserAuthRequest());
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
  }

  // Destination workspace policy is the merge of source workspace policy and pre-clone destination
  // workspace policy
  @Test
  void clone_policiesMerged() throws Exception {
    logger.info("features.isTpsEnabled(): %s".formatted(features.isTpsEnabled()));
    // Don't run the test if TPS is disabled
    if (!features.isTpsEnabled()) {
      return;
    }

    // Clean up policies from previous runs, if any exist
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);

    // Add broader region policy to destination, narrow policy on source.
    mockMvcUtils.updatePolicies(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_IOWA),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneFlexResource(
        userAccessTestUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceFlexResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName,
        /*destDescription*/ null);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockMvcUtils.getWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    Assertions.assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  void clone_copyResource() throws Exception {
    String destResourcename = TestUtils.appendRandomNumber("dest-resource-name");
    String destDescription = "new description";

    ApiFlexibleResource clonedFlexResource =
        mockMvcUtils.cloneFlexResource(
            userAccessTestUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceFlexResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourcename,
            destDescription);

    // Assert resource returned in clone flight response.
    mockMvcUtils.assertClonedControlledFlexibleResource(
        sourceFlexResource,
        clonedFlexResource,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourcename,
        destDescription,
        userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.getDefaultUserEmail());

    // Assert resource returned by get
    final ApiFlexibleResource gotResource =
        mockMvcUtils.getFlexibleResource(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedFlexResource.getMetadata().getResourceId());

    assertApiFlexibleResourceEquals(clonedFlexResource, gotResource);
  }

  @Test
  void clone_copyResource_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDescription = "new description";

    mockMvcUtils.cloneFlex_undo(
        userAccessTestUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceFlexResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName,
        destDescription);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessTestUtils.defaultUserAuthRequest(), workspaceId2, destResourceName);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneFlex_forbidden(
        userAccessTestUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceFlexResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ destResourceName,
        /*description=*/ null);
  }

  @Test
  public void clone_requesterNoWriteAccessOnDestWorkspace_throws403() throws Exception {
    final AuthenticatedUserRequest userRequest = userAccessTestUtils.defaultUserAuthRequest();
    mockMvcUtils.grantRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userRequest, workspaceId2, WsmIamRole.READER, userAccessTestUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      mockMvcUtils.cloneFlex_forbidden(
          userAccessTestUtils.secondUserAuthRequest(),
          /*sourceWorkspaceId=*/ workspaceId,
          /*sourceResourceId=*/ sourceFlexResource.getMetadata().getResourceId(),
          /*destWorkspaceId=*/ workspaceId2,
          ApiCloningInstructionsEnum.RESOURCE,
          /*destResourceName=*/ destResourceName,
          /*description=*/ null);
    } finally {
      mockMvcUtils.removeRole(
          userRequest, workspaceId, WsmIamRole.READER, userAccessTestUtils.getSecondUserEmail());
      mockMvcUtils.removeRole(
          userRequest, workspaceId2, WsmIamRole.READER, userAccessTestUtils.getSecondUserEmail());
    }
  }

  @Test
  public void clone_SecondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      String destDescription = "new description";
      ApiFlexibleResource clonedFlexResource =
          mockMvcUtils.cloneFlexResource(
              userAccessTestUtils.secondUserAuthRequest(),
              /*sourceWorkspaceId=*/ workspaceId,
              sourceFlexResource.getMetadata().getResourceId(),
              /*destWorkspaceId=*/ workspaceId2,
              ApiCloningInstructionsEnum.RESOURCE,
              destResourceName,
              destDescription);
      mockMvcUtils.assertClonedControlledFlexibleResource(
          sourceFlexResource,
          clonedFlexResource,
          /*expectedDestWorkspaceId=*/ workspaceId2,
          destResourceName,
          /*expectedResourceDescription=*/ destDescription,
          userAccessTestUtils.getSecondUserEmail(),
          userAccessTestUtils.getSecondUserEmail());

      mockMvcUtils.deleteFlexibleResource(
          userAccessTestUtils.defaultUserAuthRequest(),
          workspaceId2,
          clonedFlexResource.getMetadata().getResourceId());
    } finally {
      mockMvcUtils.removeRole(
          userAccessTestUtils.defaultUserAuthRequest(),
          workspaceId,
          WsmIamRole.READER,
          userAccessTestUtils.getSecondUserEmail());
      mockMvcUtils.removeRole(
          userAccessTestUtils.defaultUserAuthRequest(),
          workspaceId2,
          WsmIamRole.WRITER,
          userAccessTestUtils.getSecondUserEmail());
    }
  }
}
