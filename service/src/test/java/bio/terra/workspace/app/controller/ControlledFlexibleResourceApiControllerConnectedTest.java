package bio.terra.workspace.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.mocks.MockFlexibleResourceApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiFlexibleResource;
import bio.terra.workspace.generated.model.ApiFlexibleResourceAttributes;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
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
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired MockFlexibleResourceApi mockFlexibleResourceApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogService activityLogService;

  private UUID workspaceId;
  private UUID workspaceId2;
  private ApiFlexibleResource sourceFlexResource;

  private static final String sourceResourceName =
      TestUtils.appendRandomNumber("source-flexible-resource-name");
  private static final String sourceTypeNamespace = "terra";
  private static final String sourceType = "fake-flexible-type";
  private static final String sourceData = "{\"name\":\"original JSON\"}";

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockWorkspaceV1Api
            .createWorkspaceWithoutCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    workspaceId2 =
        mockWorkspaceV1Api
            .createWorkspaceWithPolicy(
                userAccessUtils.defaultUserAuthRequest(),
                new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.GROUP_POLICY_DEFAULT))
            .getId();
    // Source flex resource used in clone tests.
    sourceFlexResource =
        mockFlexibleResourceApi
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceId);
    mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceId2);
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
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);

    // Add broader region policy to destination, narrow policy on source.
    mockWorkspaceV1Api.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        /* policiesToAdd= */ ImmutableList.of(PolicyFixtures.REGION_POLICY_IOWA),
        /* policiesToRemove= */ null);
    mockWorkspaceV1Api.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        /* policiesToAdd= */ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
        /* policiesToRemove= */ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockFlexibleResourceApi.cloneFlexibleResourceAndWait(
        userAccessUtils.defaultUserAuthRequest(),
        /* sourceWorkspaceId= */ workspaceId,
        sourceFlexResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName,
        /*destDescription*/ null);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockWorkspaceV1Api.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    Assertions.assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  void clone_copyResource() throws Exception {
    String destResourcename = TestUtils.appendRandomNumber("dest-resource-name");
    String destDescription = "new description";

    ApiFlexibleResource clonedFlexResource =
        mockFlexibleResourceApi.cloneFlexibleResourceAndWait(
            userAccessUtils.defaultUserAuthRequest(),
            /* sourceWorkspaceId= */ workspaceId,
            sourceFlexResource.getMetadata().getResourceId(),
            /* destWorkspaceId= */ workspaceId2,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourcename,
            destDescription);

    // Assert resource returned in clone flight response.
    assertClonedControlledFlexibleResource(
        sourceFlexResource,
        clonedFlexResource,
        /* expectedDestWorkspaceId= */ workspaceId2,
        destResourcename,
        destDescription,
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiFlexibleResource gotResource =
        mockFlexibleResourceApi.getFlexibleResource(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedFlexResource.getMetadata().getResourceId());

    MockFlexibleResourceApi.assertApiFlexibleResourceEquals(clonedFlexResource, gotResource);
  }

  @Test
  void clone_copyResource_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDescription = "new description";
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockFlexibleResourceApi.cloneFlexibleResourceAndExpect(
        userRequest,
        /* sourceWorkspaceId= */ workspaceId,
        sourceFlexResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName,
        destDescription,
        List.of(HttpStatus.SC_INTERNAL_SERVER_ERROR),
        /* shouldUndo= */ true);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockWorkspaceV1Api.assertNoResourceWithName(userRequest, workspaceId2, destResourceName);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockFlexibleResourceApi.cloneFlexibleResourceAndExpect(
        userAccessUtils.secondUserAuthRequest(),
        /* sourceWorkspaceId= */ workspaceId,
        /* sourceResourceId= */ sourceFlexResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /* destResourceName= */ destResourceName,
        /* description= */ null,
        List.of(HttpStatus.SC_FORBIDDEN),
        /* shouldUndo= */ false);
  }

  @Test
  public void clone_requesterNoWriteAccessOnDestWorkspace_throws403() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockWorkspaceV1Api.grantRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockWorkspaceV1Api.grantRole(
        userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      mockFlexibleResourceApi.cloneFlexibleResourceAndExpect(
          userAccessUtils.secondUserAuthRequest(),
          /* sourceWorkspaceId= */ workspaceId,
          /* sourceResourceId= */ sourceFlexResource.getMetadata().getResourceId(),
          /* destWorkspaceId= */ workspaceId2,
          ApiCloningInstructionsEnum.RESOURCE,
          /* destResourceName= */ destResourceName,
          /* description= */ null,
          List.of(HttpStatus.SC_FORBIDDEN),
          /* shouldUndo= */ false);
    } finally {
      mockWorkspaceV1Api.removeRole(
          userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
      mockWorkspaceV1Api.removeRole(
          userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    }
  }

  @Test
  public void clone_SecondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    // Always remove roles before test terminates.
    try {
      String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
      String destDescription = "new description";
      ApiFlexibleResource clonedFlexResource =
          mockFlexibleResourceApi.cloneFlexibleResourceAndWait(
              userAccessUtils.secondUserAuthRequest(),
              /* sourceWorkspaceId= */ workspaceId,
              sourceFlexResource.getMetadata().getResourceId(),
              /* destWorkspaceId= */ workspaceId2,
              ApiCloningInstructionsEnum.RESOURCE,
              destResourceName,
              destDescription);
      assertClonedControlledFlexibleResource(
          sourceFlexResource,
          clonedFlexResource,
          /* expectedDestWorkspaceId= */ workspaceId2,
          destResourceName,
          /* expectedResourceDescription= */ destDescription,
          userAccessUtils.getSecondUserEmail(),
          userAccessUtils.getSecondUserEmail());
      mockFlexibleResourceApi.deleteFlexibleResource(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId2,
          clonedFlexResource.getMetadata().getResourceId());
    } finally {
      mockWorkspaceV1Api.removeRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId,
          WsmIamRole.READER,
          userAccessUtils.getSecondUserEmail());
      mockWorkspaceV1Api.removeRole(
          userAccessUtils.defaultUserAuthRequest(),
          workspaceId2,
          WsmIamRole.WRITER,
          userAccessUtils.getSecondUserEmail());
    }
  }

  public static void assertClonedControlledFlexibleResource(
      @NotNull ApiFlexibleResource originalFlexibleResource,
      ApiFlexibleResource actualFlexibleResource,
      UUID expectedDestWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    // Attributes are immutable upon cloning.
    ApiFlexibleResourceAttributes originalAttributes = originalFlexibleResource.getAttributes();

    MockFlexibleResourceApi.assertFlexibleResource(
        actualFlexibleResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        expectedDestWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        expectedCreatedBy,
        expectedLastUpdatedBy,
        originalAttributes.getTypeNamespace(),
        originalAttributes.getType(),
        originalAttributes.getData());

    MockMvcUtils.assertControlledResourceMetadata(
        actualFlexibleResource.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.SHARED_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser(),
        ApiPrivateResourceState.NOT_APPLICABLE,
        null);
  }
}
