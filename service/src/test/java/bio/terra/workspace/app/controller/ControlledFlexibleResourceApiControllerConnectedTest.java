package bio.terra.workspace.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@Tag("connected")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ControlledFlexibleResourceApiControllerConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledFlexibleResourceApiControllerConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired FeatureConfiguration features;

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
            .createWorkspaceWithoutCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    // Source flex resource used in clone tests.
    sourceFlexResource =
        mockMvcUtils
            .createFlexibleResource(
                userAccessUtils.defaultUserAuthRequest(),
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
        jobService, workspaceId, userAccessUtils.defaultUserAuthRequest());
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId2, userAccessUtils.defaultUserAuthRequest());
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  // Destination workspace policy is the merge of source workspace policy and pre-clone destination
  // workspace policy
  // TODO: Enable when TPS is fixed.
  @Test
  void clone_policiesMerged() throws Exception {
    logger.info("features.isTpsEnabled(): %s".formatted(features.isTpsEnabled()));
    // Don't run the test if TPS is disabled
    if (!features.isTpsEnabled()) {
      return;
    }

    // Clean up policies from previous runs, if any exist
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);

    // Add region policy to source workspace. Add group policy to dest workspace.
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.GROUP_POLICY),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneFlexResource(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceFlexResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName,
        /*destDescription*/ null);

    // Assert dest workspace has group and region policies
    ApiWorkspaceDescription destWorkspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.GROUP_POLICY, PolicyFixtures.REGION_POLICY));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  // Destination workspace policy is the merge of source workspace policy and pre-clone destination
  // workspace policy
  //  @Test
  //  void clone_policiesMerged() throws Exception {
  //    logger.info("features.isTpsEnabled(): %s".formatted(features.isTpsEnabled()));
  //    // Don't run the test if TPS is disabled
  //    if (!features.isTpsEnabled()) {
  //      return;
  //    }
  //
  //    // Clean up policies from previous runs, if any exist
  //    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  //    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  //
  //    // Add broader region policy to destination, narrow policy on source.
  //    mockMvcUtils.updatePolicies(
  //        userAccessUtils.defaultUserAuthRequest(),
  //        workspaceId,
  //        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_IOWA),
  //        /*policiesToRemove=*/ null);
  //    mockMvcUtils.updatePolicies(
  //        userAccessUtils.defaultUserAuthRequest(),
  //        workspaceId2,
  //        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
  //        /*policiesToRemove=*/ null);
  //
  //    // Clone resource
  //    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
  //    mockMvcUtils.cloneControlledBqDataset(
  //        userAccessUtils.defaultUserAuthRequest(),
  //        /*sourceWorkspaceId=*/ workspaceId,
  //        sourceResource.getMetadata().getResourceId(),
  //        /*destWorkspaceId=*/ workspaceId2,
  //        ApiCloningInstructionsEnum.REFERENCE,
  //        destResourceName,
  //        /*destDatasetName*/ null);
  //
  //    // Assert dest workspace policy is reduced to the narrower region.
  //    ApiWorkspaceDescription destWorkspace =
  //        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  //    assertThat(
  //        destWorkspace.getPolicies(),
  //        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA,
  // PolicyFixtures.GROUP_POLICY_DEFAULT));
  //    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));
  //
  //    // Clean up: Delete policies
  //    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
  //    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  //  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneFlex_forbidden(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceFlexResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ destResourceName,
        /*description=*/ null);
  }

  @Test
  public void clone_requesterNoWriteAccessOnDestWorkspace_throws403() throws Exception {
    final AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockMvcUtils.grantRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      mockMvcUtils.cloneFlex_forbidden(
          userAccessUtils.secondUserAuthRequest(),
          /*sourceWorkspaceId=*/ workspaceId,
          /*sourceResourceId=*/ sourceFlexResource.getMetadata().getResourceId(),
          /*destWorkspaceId=*/ workspaceId2,
          ApiCloningInstructionsEnum.RESOURCE,
          /*destResourceName=*/ destResourceName,
          /*description=*/ null);
    } finally {
      mockMvcUtils.removeRole(
          userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
      mockMvcUtils.removeRole(
          userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    }
  }

  @Test
  public void clone_SecondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      String destDescription = "new description";
      ApiFlexibleResource clonedFlexResource =
          mockMvcUtils.cloneFlexResource(
              userAccessUtils.secondUserAuthRequest(),
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
          userAccessUtils.getSecondUserEmail(),
          userAccessUtils.getSecondUserEmail());

      mockMvcUtils.deleteFlexibleResource(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId2,
          clonedFlexResource.getMetadata().getResourceId());
    } finally {
      mockMvcUtils.removeRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId,
          WsmIamRole.READER,
          userAccessUtils.getSecondUserEmail());
      mockMvcUtils.removeRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId2,
          WsmIamRole.WRITER,
          userAccessUtils.getSecondUserEmail());
    }
  }
}
