package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.mocks.MockGitRepoApi.REFERENCED_GIT_REPOS_PATH_FORMAT;
import static bio.terra.workspace.common.mocks.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.mocks.MockGitRepoApi;
import bio.terra.workspace.common.mocks.MockMvcUtils;
import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.mocks.MockWorkspaceV2Api;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiUpdateBigQueryDatasetReferenceRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.resource.model.StewardshipType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for referenced git repos. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ReferencedGcpResourceControllerGitRepoConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedGcpResourceControllerGitRepoConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockWorkspaceV1Api mockWorkspaceV1Api;
  @Autowired MockWorkspaceV2Api mockWorkspaceV2Api;
  @Autowired MockGcpApi mockGcpApi;
  @Autowired MockGitRepoApi mockGitRepoApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;

  private UUID workspaceId;
  private UUID workspaceId2;
  private String sourceResourceName;
  private String sourceGitRepoUrl;
  private ApiGitRepoResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#for-local-runs-skip-workspacecontext-creation
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
  }

  @BeforeEach
  void setUpPerTest() throws Exception {
    sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
    sourceGitRepoUrl =
        "git@github.com:DataBiosphere/%s.git"
            .formatted(TestUtils.appendRandomNumber("terra-workspace-manager"));

    sourceResource =
        mockGitRepoApi.createReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResourceName,
            sourceGitRepoUrl);
  }

  @AfterAll
  public void cleanup() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceId);
    mockWorkspaceV2Api.deleteWorkspaceAndWait(userRequest, workspaceId2);
  }

  @Test
  public void create() throws Exception {
    // Resource was created in setup()

    // Assert resource returned by create
    assertGitRepo(
        sourceResource,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        sourceResourceName,
        sourceResource.getMetadata().getDescription(),
        sourceGitRepoUrl,
        /* expectedCreatedBy= */ userAccessUtils.getDefaultUserEmail(),
        /* expectedLastUpdatedBy= */ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGitRepoResource gotResource =
        mockGitRepoApi.getReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResource, gotResource);
  }

  @Test
  public void update() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    String newGitRepoUrl = "git@github.com:DataBiosphere/terra-workspace-manager.git";
    String newResourceName = TestUtils.appendRandomNumber("newgitreferencename");
    ApiCloningInstructionsEnum newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;
    String newDescription = "This is an updated description";

    ApiGitRepoResource updatedResource =
        mockGitRepoApi.updateReferencedGitRepo(
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newResourceName,
            newDescription,
            newGitRepoUrl,
            newCloningInstruction,
            userAccessUtils.secondUserAuthRequest());

    assertGitRepo(
        updatedResource,
        newCloningInstruction,
        workspaceId,
        newResourceName,
        newDescription,
        newGitRepoUrl,
        /* expectedCreatedBy= */ userAccessUtils.getDefaultUserEmail(),
        /* expectedLastUpdatedBy= */ userAccessUtils.getSecondUserEmail());
    // clean up permission of the second user.
    mockWorkspaceV1Api.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
  }

  @Test
  public void update_throws409() throws Exception {
    String newName = TestUtils.appendRandomNumber("newgcsobjectresourcename");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockGitRepoApi.createReferencedGitRepo(userRequest, workspaceId, newName, sourceGitRepoUrl);

    mockWorkspaceV1Api.updateResourceAndExpect(
        ApiGitRepoResource.class,
        REFERENCED_GIT_REPOS_PATH_FORMAT,
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        objectMapper.writeValueAsString(
            new ApiUpdateBigQueryDatasetReferenceRequestBody().name(newName)),
        userRequest,
        HttpStatus.SC_CONFLICT);

    ApiGitRepoResource gotResource =
        mockGitRepoApi.getReferencedGitRepo(
            userRequest, workspaceId, sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResourceName, gotResource.getMetadata().getName());
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockGitRepoApi.cloneReferencedGitRepo(
        userAccessUtils.secondUserAuthRequest(),
        /* sourceWorkspaceId= */ workspaceId,
        /* sourceResourceId= */ sourceResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /* destResourceName= */ null,
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void clone_requesterNoWriteAccessOnDestWorkspace_throws403() throws Exception {
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockWorkspaceV1Api.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());

    mockGitRepoApi.cloneReferencedGitRepo(
        userAccessUtils.secondUserAuthRequest(),
        /* sourceWorkspaceId= */ workspaceId,
        /* sourceResourceId= */ sourceResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /* destResourceName= */ null,
        HttpStatus.SC_FORBIDDEN);

    mockWorkspaceV1Api.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockWorkspaceV1Api.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
  }

  @Test
  public void clone_secondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
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

    ApiGitRepoResource clonedResource =
        mockGitRepoApi.cloneReferencedGitRepo(
            userAccessUtils.secondUserAuthRequest(),
            /* sourceWorkspaceId= */ workspaceId,
            /* sourceResourceId= */ sourceResource.getMetadata().getResourceId(),
            /* destWorkspaceId= */ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            /* destResourceName= */ null);

    assertClonedGitRepo(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        sourceResourceName,
        sourceGitRepoUrl,
        /* expectedCreatedBy= */ userAccessUtils.getSecondUserEmail(),
        userAccessUtils.secondUserAuthRequest());

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
    mockGcpApi.deleteReferencedGcsBucket(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        clonedResource.getMetadata().getResourceId());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    ApiGitRepoResource clonedResource =
        mockGitRepoApi.cloneReferencedGitRepo(
            userRequest,
            /* sourceWorkspaceId= */ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /* destWorkspaceId= */ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockWorkspaceV1Api.assertNoResourceWithName(userRequest, workspaceId, destResourceName);
  }

  @Test
  void clone_copyReference_sameWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGitRepoResource clonedResource =
        mockGitRepoApi.cloneReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            /* sourceWorkspaceId= */ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /* destWorkspaceId= */ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedGitRepo(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        destResourceName,
        sourceResource.getAttributes().getGitRepoUrl(),
        /* expectedCreatedBy= */ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    ApiGitRepoResource gotResource =
        mockGitRepoApi.getReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGitRepoResource clonedResource =
        mockGitRepoApi.cloneReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            /* sourceWorkspaceId= */ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /* destWorkspaceId= */ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedGitRepo(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        destResourceName,
        sourceGitRepoUrl,
        /* expectedCreatedBy= */ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    ApiGitRepoResource gotResource =
        mockGitRepoApi.getReferencedGitRepo(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
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
    mockGitRepoApi.cloneReferencedGitRepo(
        userAccessUtils.defaultUserAuthRequest(),
        /* sourceWorkspaceId= */ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /* destWorkspaceId= */ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockWorkspaceV1Api.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockWorkspaceV1Api.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  private void assertGitRepo(
      ApiGitRepoResource actualResource,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedGitRepoUrl,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        /* expectedCloudPlatform= */ null,
        ApiResourceType.GIT_REPO,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /* expectedResourceLineage= */ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedGitRepoUrl, actualResource.getAttributes().getGitRepoUrl());
  }

  private void assertClonedGitRepo(
      ApiGitRepoResource actualResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedGitRepoUrl,
      String expectedCreatedBy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualResource.getMetadata(),
        /* expectedCloudPlatform= */ null,
        ApiResourceType.GIT_REPO,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        RESOURCE_DESCRIPTION,
        /* sourceWorkspaceId= */ workspaceId,
        /* sourceResourceId= */ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy,
        StewardshipType.REFERENCED,
        userRequest);

    assertEquals(expectedGitRepoUrl, actualResource.getAttributes().getGitRepoUrl());
  }
}
