package bio.terra.workspace.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for referenced TDR snapshots. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ReferencedGcpResourceControllerDataRepoSnapshotTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedGcpResourceControllerDataRepoSnapshotTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;

  private UUID workspaceId;
  private UUID workspaceId2;

  private String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private String sourceInstanceName = TestUtils.appendRandomNumber("source-instance-name");
  private String sourceSnapshot = TestUtils.appendRandomNumber("source-snapshot");
  private ApiDataRepoSnapshotResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#for-local-runs-skip-workspacecontext-creation
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

    sourceResource =
        mockMvcUtils.createReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            sourceResourceName,
            sourceInstanceName,
            sourceSnapshot);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  public void create() throws Exception {
    // Resource was created in setup()

    // Assert resource returned by create
    assertDataRepoSnapshot(
        sourceResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        sourceResourceName,
        sourceInstanceName,
        sourceSnapshot,
        userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResource, gotResource);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneReferencedDataRepoSnapshot(
        userAccessUtils.secondUserAuthRequest(),
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
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());

    mockMvcUtils.cloneReferencedDataRepoSnapshot(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /*destResourceName=*/ null,
        HttpStatus.SC_FORBIDDEN);

    mockMvcUtils.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        WsmIamRole.READER,
        userAccessUtils.getSecondUserEmail());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  @Test
  void clone_copyReference_sameWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedDataRepoSnapshot(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        destResourceName,
        sourceInstanceName,
        sourceSnapshot,
        userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    final ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedDataRepoSnapshot(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        destResourceName,
        sourceInstanceName,
        sourceSnapshot,
        userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    final ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
  }

  // Destination workspace policy is the merge of source workspace policy and pre-clone destination
  // workspace policy
  @Test
  @Disabled("Enable after PF-2217 is fixed")
  void clone_policiesMerged() throws Exception {
    logger.info("features.isTpsEnabled(): %s".formatted(features.isTpsEnabled()));
    // Don't run the test if TPS is disabled
    if (!features.isTpsEnabled()) {
      return;
    }

    // Clean up policies from previous runs, if any exist
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);

    // Add group policy to source workspace. Add region policy to dest workspace.
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.GROUP_POLICY),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneReferencedDataRepoSnapshot(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

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

  private void assertDataRepoSnapshot(
      ApiDataRepoSnapshotResource actualResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedInstanceName,
      String expectedSnapshot,
      String expectedCreatedBy) {
    mockMvcUtils.assertResourceMetadata(
        actualResource.getMetadata(),
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.DATA_REPO_SNAPSHOT,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy);

    assertEquals(expectedInstanceName, actualResource.getAttributes().getInstanceName());
    assertEquals(expectedSnapshot, actualResource.getAttributes().getSnapshot());
  }

  private void assertClonedDataRepoSnapshot(
      ApiDataRepoSnapshotResource actualResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedInstanceName,
      String expectedSnapshot,
      String expectedCreatedBy) {
    mockMvcUtils.assertClonedResourceMetadata(
        actualResource.getMetadata(),
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.DATA_REPO_SNAPSHOT,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy);

    assertEquals(expectedInstanceName, actualResource.getAttributes().getInstanceName());
    assertEquals(expectedSnapshot, actualResource.getAttributes().getSnapshot());
  }
}
