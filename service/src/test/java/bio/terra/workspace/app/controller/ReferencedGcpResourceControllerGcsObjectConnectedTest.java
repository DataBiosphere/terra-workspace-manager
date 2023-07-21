package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.mocks.MockGcpApi.REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.mocks.MockGcpApi;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiGcpGcsObjectResource;
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

/** Connected tests for referenced GCS objects. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connectedPlus")
@TestInstance(Lifecycle.PER_CLASS)
public class ReferencedGcpResourceControllerGcsObjectConnectedTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ReferencedGcpResourceControllerGcsObjectConnectedTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired MockGcpApi mockGcpApi;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired FeatureConfiguration features;

  private UUID workspaceId;
  private UUID workspaceId2;

  private String sourceResourceName;
  private String sourceBucketName;
  private String sourceFileName;
  private ApiGcpGcsObjectResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager/blob/main/DEVELOPMENT.md#for-local-runs-skip-workspacecontext-creation
  @BeforeAll
  public void setup() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    workspaceId =
        mockMvcUtils.createWorkspaceWithCloudContext(userRequest, apiCloudPlatform).getId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithPolicy(
                userRequest,
                new ApiWsmPolicyInputs().addInputsItem(PolicyFixtures.GROUP_POLICY_DEFAULT))
            .getId();
  }

  @BeforeEach
  void setUpPerTest() throws Exception {
    sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
    sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");
    sourceFileName = TestUtils.appendRandomNumber("source-file-name");
    sourceResource =
        mockGcpApi.createReferencedGcsObject(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResourceName,
            sourceBucketName,
            sourceFileName);
  }

  @AfterAll
  public void cleanup() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockMvcUtils.deleteWorkspaceV2AndWait(userRequest, workspaceId);
    mockMvcUtils.deleteWorkspaceV2AndWait(userRequest, workspaceId2);
  }

  @Test
  public void create() throws Exception {
    // Resource was created in setup()

    // Assert resource returned by create
    assertGcsObject(
        sourceResource,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        sourceResource.getMetadata().getName(),
        sourceResource.getMetadata().getDescription(),
        sourceResource.getAttributes().getBucketName(),
        sourceResource.getAttributes().getFileName(),
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpGcsObjectResource gotResource =
        mockGcpApi.getReferencedGcsObject(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResource, gotResource);
  }

  @Test
  public void update() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockMvcUtils.grantRole(
        userRequest, workspaceId, WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());

    var newName = TestUtils.appendRandomNumber("newgcsobjectname");
    var newBucketName = TestUtils.appendRandomNumber("newgcsbucketname");
    var newObjectName = TestUtils.appendRandomNumber("newobjectname");
    var newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;
    String newDescription = "This is an updated description";
    ApiGcpGcsObjectResource updatedResource =
        mockGcpApi.updateReferencedGcsObject(
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newName,
            newDescription,
            newBucketName,
            newObjectName,
            newCloningInstruction,
            userAccessUtils.secondUserAuthRequest());

    assertGcsObject(
        updatedResource,
        newCloningInstruction,
        workspaceId,
        newName,
        newDescription,
        newBucketName,
        newObjectName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userRequest, workspaceId, WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());
  }

  @Test
  public void update_throws409() throws Exception {
    var newName = TestUtils.appendRandomNumber("newgcsobjectresourcename");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    mockGcpApi.createReferencedGcsObject(
        userRequest, workspaceId, newName, sourceBucketName, sourceFileName);

    mockMvcUtils.postExpect(
        userRequest,
        objectMapper.writeValueAsString(
            new ApiUpdateBigQueryDatasetReferenceRequestBody().name(newName)),
        String.format(
            REFERENCED_GCP_GCS_OBJECTS_PATH_FORMAT,
            workspaceId,
            sourceResource.getMetadata().getResourceId()),
        HttpStatus.SC_CONFLICT);

    ApiGcpGcsObjectResource gotResource =
        mockGcpApi.getReferencedGcsObject(
            userRequest, workspaceId, sourceResource.getMetadata().getResourceId());
    assertEquals(sourceResourceName, gotResource.getMetadata().getName());
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockGcpApi.cloneReferencedGcsObjectAndExpect(
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockMvcUtils.grantRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());

    mockGcpApi.cloneReferencedGcsObjectAndExpect(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        /*destResourceName=*/ null,
        HttpStatus.SC_FORBIDDEN);

    mockMvcUtils.removeRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userRequest, workspaceId2, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
  }

  @Test
  public void clone_secondUserHasWriteAccessOnDestWorkspace_succeeds() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
    mockMvcUtils.grantRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockMvcUtils.grantRole(
        userRequest, workspaceId2, WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());

    ApiGcpGcsObjectResource clonedResource =
        mockGcpApi.cloneReferencedGcsObject(
            userAccessUtils.secondUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            /*destResourceName=*/ null);

    assertClonedGcsObject(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        sourceResourceName,
        sourceBucketName,
        sourceFileName,
        /*expectedCreatedBy=*/ userAccessUtils.getSecondUserEmail(),
        userAccessUtils.secondUserAuthRequest());
    mockMvcUtils.removeRole(
        userRequest, workspaceId, WsmIamRole.READER, userAccessUtils.getSecondUserEmail());
    mockMvcUtils.removeRole(
        userRequest, workspaceId2, WsmIamRole.WRITER, userAccessUtils.getSecondUserEmail());
    mockGcpApi.deleteReferencedGcsObject(
        userRequest, workspaceId2, clonedResource.getMetadata().getResourceId());
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    ApiGcpGcsObjectResource clonedResource =
        mockGcpApi.cloneReferencedGcsObject(
            userRequest,
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(userRequest, workspaceId, destResourceName);
  }

  @Test
  void clone_copyReference_sameWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    ApiGcpGcsObjectResource clonedResource =
        mockGcpApi.cloneReferencedGcsObject(
            userRequest,
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedGcsObject(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId,
        destResourceName,
        sourceBucketName,
        sourceFileName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsObjectResource gotResource =
        mockGcpApi.getReferencedGcsObject(
            userRequest, workspaceId, clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);
  }

  @Test
  void clone_copyReference_differentWorkspace() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    ApiGcpGcsObjectResource clonedResource =
        mockGcpApi.cloneReferencedGcsObject(
            userRequest,
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName);

    // Assert resource returned in clone flight response
    assertClonedGcsObject(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        ApiCloningInstructionsEnum.NOTHING,
        workspaceId2,
        destResourceName,
        sourceBucketName,
        sourceFileName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsObjectResource gotResource =
        mockGcpApi.getReferencedGcsObject(
            userRequest, workspaceId2, clonedResource.getMetadata().getResourceId());
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

    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    // Clean up policies from previous runs, if any exist
    mockMvcUtils.deletePolicies(userRequest, workspaceId);
    mockMvcUtils.deletePolicies(userRequest, workspaceId2);

    // Add broader region policy to destination, narrow policy on source.
    mockMvcUtils.updatePolicies(
        userRequest,
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_IOWA),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userRequest,
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockGcpApi.cloneReferencedGcsObject(
        userRequest,
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace = mockMvcUtils.getWorkspace(userRequest, workspaceId2);
    assertThat(
        destWorkspace.getPolicies(),
        containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA, PolicyFixtures.GROUP_POLICY_DEFAULT));
    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userRequest, workspaceId);
    mockMvcUtils.deletePolicies(userRequest, workspaceId2);
  }

  private void assertGcsObject(
      ApiGcpGcsObjectResource actualResource,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedDescription,
      String expectedBucketName,
      String expectedFileName,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.GCS_OBJECT,
        ApiStewardshipType.REFERENCED,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedBucketName, actualResource.getAttributes().getBucketName());
    assertEquals(expectedFileName, actualResource.getAttributes().getFileName());
  }

  private void assertClonedGcsObject(
      ApiGcpGcsObjectResource actualResource,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedBucketName,
      String expectedFileName,
      String expectedCreatedBy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualResource.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.GCS_OBJECT,
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

    assertEquals(expectedBucketName, actualResource.getAttributes().getBucketName());
    assertEquals(expectedFileName, actualResource.getAttributes().getFileName());
  }
}
