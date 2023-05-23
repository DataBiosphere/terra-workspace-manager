package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiBqDataTableEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
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

/** Connected tests for referenced BQ tables. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ReferencedGcpResourceControllerBqTableConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedGcpResourceControllerBqTableConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;
  @Autowired SamService samService;

  private UUID workspaceId;
  private String projectId;
  private UUID workspaceId2;

  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceDatasetName = TestUtils.appendRandomNumber("source-dataset-name");
  private final String sourceTableId = TestUtils.appendRandomNumber("source-table-id");
  private ApiGcpBigQueryDataTableResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#for-local-runs-skip-workspacecontext-creation
  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    projectId = workspace.getGcpContext().getProjectId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithPolicy(
                userAccessUtils.defaultUserAuthRequest(),
                new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.GROUP_POLICY_DEFAULT))
            .getId();

    sourceResource =
        mockMvcUtils.createReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResourceName,
            projectId,
            sourceDatasetName,
            sourceTableId);
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
    assertBqTable(
        sourceResource,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        projectId,
        sourceDatasetName,
        sourceTableId,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpBigQueryDataTableResource gotResource =
        mockMvcUtils.getReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertApiBqDataTableEquals(sourceResource, gotResource);
  }

  @Test
  public void update() throws Exception {
    mockMvcUtils.grantRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());

    var newName = TestUtils.appendRandomNumber("newdatatableresourcename");
    var newDescription = "This is an updated description";
    var newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;
    var newProjectId = TestUtils.appendRandomNumber("newProjectid");
    var newDataset = TestUtils.appendRandomNumber("newdataset");
    var newTable = TestUtils.appendRandomNumber("newtable");
    ApiGcpBigQueryDataTableResource updatedResource =
        mockMvcUtils.updateReferencedBqTable(
            userAccessUtils.secondUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newName,
            newDescription,
            newCloningInstruction,
            newProjectId,
            newDataset,
            newTable);
    ApiGcpBigQueryDataTableResource gotResource =
        mockMvcUtils.getReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(updatedResource, gotResource);
    assertBqTable(
        updatedResource,
        newCloningInstruction,
        workspaceId,
        newName,
        newDescription,
        newProjectId,
        newDataset,
        newTable,
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.updateReferencedBqTable(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        ApiCloningInstructionsEnum.NOTHING,
        projectId,
        sourceDatasetName,
        sourceTableId);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneReferencedBqTable(
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

    mockMvcUtils.cloneReferencedBqTable(
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
  public void clone_secondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
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

    ApiGcpBigQueryDataTableResource clonedResource =
        mockMvcUtils.cloneReferencedBqTable(
            userAccessUtils.secondUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            /*destResourceName=*/ null);

    assertClonedBqTable(
        clonedResource,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        sourceResourceName,
        projectId,
        sourceDatasetName,
        sourceTableId,
        /*expectedCreatedBy=*/ userAccessUtils.getSecondUserEmail(),
        userAccessUtils.secondUserAuthRequest());
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
    mockMvcUtils.deleteBqDataTable(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        clonedResource.getMetadata().getResourceId());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDataTableResource clonedResource =
        mockMvcUtils.cloneReferencedBqTable(
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
    ApiGcpBigQueryDataTableResource clonedResource =
        mockMvcUtils.cloneReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedBqTable(
        clonedResource,
        ApiCloningInstructionsEnum.NOTHING,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        projectId,
        sourceDatasetName,
        sourceTableId,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDataTableResource gotResource =
        mockMvcUtils.getReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDataTableEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDataTableResource clonedResource =
        mockMvcUtils.cloneReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedBqTable(
        clonedResource,
        ApiCloningInstructionsEnum.NOTHING,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        projectId,
        sourceDatasetName,
        sourceTableId,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDataTableResource gotResource =
        mockMvcUtils.getReferencedBqTable(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDataTableEquals(clonedResource, gotResource);
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
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);

    // Add broader region policy to destination, narrow policy on source.
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_IOWA),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneReferencedBqTable(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  private void assertBqTable(
      ApiGcpBigQueryDataTableResource actualResource,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedProjectId,
      String expectedDatasetName,
      String expectedTableId,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.BIG_QUERY_DATA_TABLE,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedProjectId, actualResource.getAttributes().getProjectId());
    assertEquals(expectedDatasetName, actualResource.getAttributes().getDatasetId());
    assertEquals(expectedTableId, actualResource.getAttributes().getDataTableId());
  }

  private void assertClonedBqTable(
      ApiGcpBigQueryDataTableResource actualResource,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedProjectId,
      String expectedDatasetName,
      String expectedTableId,
      String expectedCreatedBy,
      AuthenticatedUserRequest cloneUserRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.BIG_QUERY_DATA_TABLE,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        RESOURCE_DESCRIPTION,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy,
        StewardshipType.REFERENCED,
        cloneUserRequest);

    assertEquals(expectedProjectId, actualResource.getAttributes().getProjectId());
    assertEquals(expectedDatasetName, actualResource.getAttributes().getDatasetId());
    assertEquals(expectedTableId, actualResource.getAttributes().getDataTableId());
  }
}
