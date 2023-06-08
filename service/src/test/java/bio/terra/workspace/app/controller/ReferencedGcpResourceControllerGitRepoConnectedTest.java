package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.testutils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.testfixtures.PolicyFixtures;
import bio.terra.workspace.common.testutils.MockMvcUtils;
import bio.terra.workspace.common.testutils.TestUtils;
import bio.terra.workspace.connected.UserAccessTestUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiGitRepoResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
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
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessTestUtils userAccessTestUtils;
  @Autowired FeatureConfiguration features;

  private UUID workspaceId;
  private UUID workspaceId2;

  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceGitRepoUrl =
      "git@github.com:DataBiosphere/%s.git"
          .formatted(TestUtils.appendRandomNumber("terra-workspace-manager"));
  private ApiGitRepoResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#for-local-runs-skip-workspacecontext-creation
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

    sourceResource =
        mockMvcUtils.createReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResourceName,
            sourceGitRepoUrl);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
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
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessTestUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGitRepoResource gotResource =
        mockMvcUtils.getReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResource, gotResource);
  }

  @Test
  public void update() throws Exception {
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());
    String newGitRepoUrl = "git@github.com:DataBiosphere/terra-workspace-manager.git";
    String newResourceName = TestUtils.appendRandomNumber("newgitreferencename");
    ApiCloningInstructionsEnum newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;
    String newDescription = "This is an updated description";

    ApiGitRepoResource updatedResource =
        mockMvcUtils.updateReferencedGitRepo(
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newResourceName,
            newDescription,
            newGitRepoUrl,
            newCloningInstruction,
            userAccessTestUtils.secondUserAuthRequest());

    // Assert resource returned by get
    // Update the sourceResource to the updated one as all the tests are sharing
    // the same resource.
    ApiGitRepoResource getResource =
        mockMvcUtils.getReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(updatedResource, getResource);
    assertGitRepo(
        updatedResource,
        newCloningInstruction,
        workspaceId,
        newResourceName,
        newDescription,
        newGitRepoUrl,
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessTestUtils.getSecondUserEmail());
    // clean up permission of the second user.
    mockMvcUtils.removeRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.updateReferencedGitRepo(
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        sourceGitRepoUrl,
        ApiCloningInstructionsEnum.NOTHING,
        userAccessTestUtils.defaultUserAuthRequest());
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneReferencedGitRepo(
        userAccessTestUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /*destResourceName=*/ null,
        HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void clone_requesterNoWriteAccessOnDestWorkspace_throws403() throws Exception {
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());

    mockMvcUtils.cloneReferencedGitRepo(
        userAccessTestUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /*destResourceName=*/ null,
        HttpStatus.SC_FORBIDDEN);

    mockMvcUtils.removeRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessTestUtils.getSecondUserEmail());
  }

  @Test
  public void clone_secondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
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

    ApiGitRepoResource clonedResource =
        mockMvcUtils.cloneReferencedGitRepo(
            userAccessTestUtils.secondUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            /*destResourceName=*/ null);

    assertClonedGitRepo(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        sourceResourceName,
        sourceGitRepoUrl,
        /*expectedCreatedBy=*/ userAccessTestUtils.getSecondUserEmail(),
        userAccessTestUtils.secondUserAuthRequest());

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
    mockMvcUtils.deleteReferencedGcsBucket(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        clonedResource.getMetadata().getResourceId());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGitRepoResource clonedResource =
        mockMvcUtils.cloneReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessTestUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  @Test
  void clone_copyReference_sameWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGitRepoResource clonedResource =
        mockMvcUtils.cloneReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
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
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGitRepoResource gotResource =
        mockMvcUtils.getReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGitRepoResource clonedResource =
        mockMvcUtils.cloneReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
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
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGitRepoResource gotResource =
        mockMvcUtils.getReferencedGitRepo(
            userAccessTestUtils.defaultUserAuthRequest(),
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
    mockMvcUtils.cloneReferencedGitRepo(
        userAccessTestUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockMvcUtils.getWorkspace(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessTestUtils.defaultUserAuthRequest(), workspaceId2);
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
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.GIT_REPO,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
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
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.GIT_REPO,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        RESOURCE_DESCRIPTION,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy,
        StewardshipType.REFERENCED,
        userRequest);

    assertEquals(expectedGitRepoUrl, actualResource.getAttributes().getGitRepoUrl());
  }
}
