package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.testfixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.testutils.MockMvcUtils.assertApiDataRepoEquals;
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
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
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

/** Connected tests for referenced TDR snapshots. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ReferencedGcpResourceControllerDataRepoSnapshotConnectedTest
    extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedGcpResourceControllerDataRepoSnapshotConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessTestUtils userAccessTestUtils;
  @Autowired FeatureConfiguration features;

  private UUID workspaceId;
  private UUID workspaceId2;

  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceInstanceName = TestUtils.appendRandomNumber("source-instance-name");
  private final String sourceSnapshot = UUID.randomUUID().toString();
  private ApiDataRepoSnapshotResource sourceResource;

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
        mockMvcUtils.createReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            sourceResourceName,
            sourceInstanceName,
            sourceSnapshot);
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
    assertDataRepoSnapshot(
        sourceResource,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        sourceInstanceName,
        sourceSnapshot,
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessTestUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertApiDataRepoEquals(sourceResource, gotResource);
  }

  @Test
  public void update() throws Exception {
    mockMvcUtils.grantRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());

    var newName = TestUtils.appendRandomNumber("newdatareporesourcename");
    var newDescription = "This is an updated description";
    var newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;
    var newInstanceName = TestUtils.appendRandomNumber("newinstance");
    var newSnapshot = TestUtils.appendRandomNumber("newsnapshot");
    ApiDataRepoSnapshotResource updatedResource =
        mockMvcUtils.updateReferencedDataRepoSnapshot(
            userAccessTestUtils.secondUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newName,
            newDescription,
            newSnapshot,
            newInstanceName,
            newCloningInstruction);
    ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(updatedResource, gotResource);
    assertDataRepoSnapshot(
        updatedResource,
        newCloningInstruction,
        workspaceId,
        newName,
        newDescription,
        newInstanceName,
        newSnapshot,
        userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessTestUtils.getSecondUserEmail());
    mockMvcUtils.updateReferencedDataRepoSnapshot(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        sourceSnapshot,
        sourceInstanceName,
        ApiCloningInstructionsEnum.NOTHING);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneReferencedDataRepoSnapshot(
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

    mockMvcUtils.cloneReferencedDataRepoSnapshot(
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
  public void clone_writerHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
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

    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessTestUtils.secondUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            /*destResourceName=*/ null);
    assertClonedDataRepoSnapshot(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        sourceResourceName,
        sourceResource.getMetadata().getDescription(),
        sourceInstanceName,
        sourceSnapshot,
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
    mockMvcUtils.deleteDataRepoSnapshot(
        userAccessTestUtils.defaultUserAuthRequest(),
        workspaceId2,
        clonedResource.getMetadata().getResourceId());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
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
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
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
        sourceResource.getMetadata().getDescription(),
        sourceInstanceName,
        sourceSnapshot,
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertApiDataRepoEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiDataRepoSnapshotResource clonedResource =
        mockMvcUtils.cloneReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
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
        sourceResource.getMetadata().getDescription(),
        sourceInstanceName,
        sourceSnapshot,
        /*expectedCreatedBy=*/ userAccessTestUtils.getDefaultUserEmail(),
        userAccessTestUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiDataRepoSnapshotResource gotResource =
        mockMvcUtils.getReferencedDataRepoSnapshot(
            userAccessTestUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertApiDataRepoEquals(clonedResource, gotResource);
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
    mockMvcUtils.cloneReferencedDataRepoSnapshot(
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

  private void assertDataRepoSnapshot(
      ApiDataRepoSnapshotResource actualResource,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedInstanceName,
      String expectedSnapshot,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.DATA_REPO_SNAPSHOT,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedInstanceName, actualResource.getAttributes().getInstanceName());
    assertEquals(expectedSnapshot, actualResource.getAttributes().getSnapshot());
  }

  private void assertClonedDataRepoSnapshot(
      ApiDataRepoSnapshotResource actualResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedInstanceName,
      String expectedSnapshot,
      String expectedCreatedBy,
      AuthenticatedUserRequest cloneUserRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualResource.getMetadata(),
        /*expectedCloudPlatform=*/ null,
        ApiResourceType.DATA_REPO_SNAPSHOT,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy,
        StewardshipType.REFERENCED,
        cloneUserRequest);

    assertEquals(expectedInstanceName, actualResource.getAttributes().getInstanceName());
    assertEquals(expectedSnapshot, actualResource.getAttributes().getSnapshot());
  }
}
