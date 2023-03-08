package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiBqDatasetEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.model.StewardshipType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.bigquery.model.Dataset;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for controlled BQ datasets. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Disabled("With the permission propagation delay, this test is just too unstable.")
@Tag("connected")
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerBqDatasetTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledGcpResourceApiControllerBqDatasetTest.class);

  // GCP default is us-central1. Use something different, so we know this is copied to clone
  // correctly.
  private static final String LOCATION = "us-west4";
  // GCP default is to not set this. Set this, so we know this is copied to clone correctly.
  private static final long DEFAULT_TABLE_LIFETIME = 10000000;
  // GCP default is to not set this. Set this, so we know this is copied to clone correctly.
  private static final long DEFAULT_PARTITION_LIFETIME = 10000001;

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogService activityLogService;
  @Autowired SamService samService;

  // Store workspace ID instead of workspace, we can easily use existing workspaces
  // for local development.
  private UUID workspaceId;
  private String projectId;
  private UUID workspaceId2;
  private String projectId2;

  private String sourceResourceName = TestUtils.appendRandomNumber("sourceresourcename");
  private String sourceDatasetName = TestUtils.appendRandomNumber("sourcedatasetname");
  private ApiGcpBigQueryDatasetResource sourceResource;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager#for-local-runs-skip-workspacecontext-creation
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
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    ApiWorkspaceDescription workspace2 =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    projectId2 = workspace2.getGcpContext().getProjectId();

    // Note to resource authors: Set all request fields (to non-default values).
    sourceResource =
        mockMvcUtils
            .createControlledBqDataset(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                sourceResourceName,
                sourceDatasetName,
                LOCATION,
                DEFAULT_TABLE_LIFETIME,
                DEFAULT_PARTITION_LIFETIME)
            .getBigQueryDataset();
    cloudUtils.populateBqTable(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        sourceResource.getAttributes().getProjectId(),
        sourceDatasetName);
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

  @Test
  public void create() throws Exception {
    // Resource was created in setup()

    // Assert resource returned by create
    assertBqDataset(
        sourceResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        projectId,
        sourceDatasetName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());
    // Assert resource returned by get
    ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertApiBqDatasetEquals(sourceResource, gotResource);

    // Call GCP directly
    cloudUtils.assertBqTableContents(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        gotResource.getAttributes().getProjectId(),
        gotResource.getAttributes().getDatasetId());
    assertBqDatasetAttributes(
        projectId,
        gotResource.getAttributes().getDatasetId(),
        LOCATION,
        DEFAULT_TABLE_LIFETIME,
        DEFAULT_PARTITION_LIFETIME);
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
    ApiGcpBigQueryDatasetResource updatedResource =
        mockMvcUtils.updateControlledBqDataset(
            userAccessUtils.secondUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            newName,
            newDescription,
            newCloningInstruction);

    ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId());
    assertEquals(updatedResource, gotResource);
    assertBqDataset(
        gotResource,
        ApiStewardshipType.CONTROLLED,
        newCloningInstruction,
        workspaceId,
        newName,
        newDescription,
        projectId,
        sourceDatasetName,
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.getSecondUserEmail());

    mockMvcUtils.removeRole(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.getSecondUserEmail());
    mockMvcUtils.updateControlledBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId,
        sourceResource.getMetadata().getResourceId(),
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        ApiCloningInstructionsEnum.DEFINITION);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneControlledBqDatasetAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destDatasetName=*/ null,
        /*destLocation=*/ null,
        /*defaultTableLifetime=*/ null,
        /*defaultPartitionLifetime=*/ null,
        List.of(HttpStatus.SC_FORBIDDEN),
        /*shouldUndo=*/ false);
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

    mockMvcUtils.cloneControlledBqDatasetAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destDatasetName=*/ null,
        /*destLocation=*/ null,
        /*defaultTableLifetime=*/ null,
        /*defaultPartitionLifetime=*/ null,
        List.of(HttpStatus.SC_FORBIDDEN),
        /*shouldUndo=*/ false);

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

    ApiGcpBigQueryDatasetResource clonedBqDataset =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.secondUserAuthRequest(),
            workspaceId,
            sourceResource.getMetadata().getResourceId(),
            workspaceId2,
            ApiCloningInstructionsEnum.RESOURCE,
            /*destResourceName=*/ null,
            /*destDatasetName=*/ null,
            /*destLocation=*/ null,
            /*defaultTableLifetime=*/ null,
            /*defaultPartitionLifetime=*/ null);

    assertClonedControlledBqDataset(
        clonedBqDataset,
        workspaceId2,
        sourceResourceName,
        sourceResource.getMetadata().getDescription(),
        projectId2,
        sourceDatasetName,
        LOCATION,
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
    mockMvcUtils.deleteBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        clonedBqDataset.getMetadata().getResourceId(),
        StewardshipType.CONTROLLED);
  }

  // Tests getUniquenessCheckAttributes() works
  @Test
  void clone_duplicateDatasetName_jobThrows409() throws Exception {
    ApiErrorReport errorReport =
        mockMvcUtils.cloneControlledBqDataset_jobError(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.RESOURCE,
            /*destResourceName=*/ null,
            /*destDatasetName=*/ sourceDatasetName,
            HttpStatus.SC_CONFLICT);
    assertThat(
        errorReport.getMessage(), equalTo("A resource with matching attributes already exists"));
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName,
            /*destDatasetName=*/ null);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  @Test
  void clone_copyDefinition() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    Long defaultTableLifetime = 6200L;
    Long defaultPartitionLifetime = 6201L;
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.DEFINITION,
            destResourceName,
            /*destDatasetName=*/ null,
            destLocation,
            defaultTableLifetime,
            defaultPartitionLifetime);

    // Assert resource returned in clone flight response
    assertClonedControlledBqDataset(
        clonedResource,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        sourceResource.getMetadata().getDescription(),
        /*expectedProjectId=*/ projectId2,
        /*expectedDatasetName=*/ sourceDatasetName,
        LOCATION,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDatasetEquals(clonedResource, gotResource);

    // Call GCP directly
    cloudUtils.assertDatasetHasNoTables(
        userAccessUtils.defaultUserAuthRequest(),
        projectId2,
        gotResource.getAttributes().getDatasetId());
    assertBqDatasetAttributes(
        projectId2,
        gotResource.getAttributes().getDatasetId(),
        LOCATION,
        // TODO(PF-2269): Change to DEFAULT_TABLE_LIFETIME after PF-2269 is fixed
        /*defaultTableLifetime*/ null,
        // TODO(PF-2269): Change to DEFAULT_PARTITION_LIFETIME after PF-2269 is fixed
        /*defaultPartitionLifetime*/ null);
    mockMvcUtils.deleteBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        workspaceId2,
        gotResource.getMetadata().getResourceId(),
        /*isControlled=*/ StewardshipType.REFERENCED);
  }

  @Test
  void clone_copyResource_sameWorkspace() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    Long defaultTableLifetime = 6200L;
    Long defaultPartitionLifetime = 6201L;
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDatasetName = TestUtils.appendRandomNumber("dest-dataset-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourceName,
            destDatasetName,
            destLocation,
            defaultTableLifetime,
            defaultPartitionLifetime);

    // Assert resource returned in clone flight response
    assertClonedControlledBqDataset(
        clonedResource,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        sourceResource.getMetadata().getDescription(),
        /*expectedProjectId=*/ projectId,
        /*expectedDatasetName=*/ destDatasetName,
        destLocation,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDatasetEquals(clonedResource, gotResource);

    // Call GCP directly
    cloudUtils.assertBqTableContents(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        gotResource.getAttributes().getDatasetId());
    assertBqDatasetAttributes(
        projectId,
        gotResource.getAttributes().getDatasetId(),
        destLocation,
        // TODO(PF-2269): Change to DEFAULT_TABLE_LIFETIME after PF-2269 is fixed
        /*defaultTableLifetime*/ null,
        // TODO(PF-2269): Change to DEFAULT_PARTITION_LIFETIME after PF-2269 is fixed
        /*defaultPartitionLifetime*/ null);
  }

  @Test
  void clone_copyResource_differentWorkspace() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    Long defaultTableLifetime = 6200L;
    Long defaultPartitionLifetime = 6201L;
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDatasetName = TestUtils.appendRandomNumber("dest-dataset-name");

    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourceName,
            destDatasetName,
            destLocation,
            defaultTableLifetime,
            defaultPartitionLifetime);

    // Assert resource returned in clone flight response
    assertClonedControlledBqDataset(
        clonedResource,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        sourceResource.getMetadata().getDescription(),
        /*expectedProjectId=*/ projectId2,
        /*expectedDatasetName=*/ destDatasetName,
        destLocation,
        userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDatasetEquals(clonedResource, gotResource);

    // Call GCP directly
    cloudUtils.assertBqTableContents(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId2,
        gotResource.getAttributes().getDatasetId());
    assertBqDatasetAttributes(
        projectId2,
        gotResource.getAttributes().getDatasetId(),
        destLocation,
        // TODO(PF-2269): Change to DEFAULT_TABLE_LIFETIME after PF-2269 is fixed
        /*defaultTableLifetime*/ null,
        // TODO(PF-2269): Change to DEFAULT_PARTITION_LIFETIME after PF-2269 is fixed
        /*defaultPartitionLifetime*/ null);
  }

  @Test
  void clone_copyReference() throws Exception {
    // Source resource is COPY_DEFINITION

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceResource.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.REFERENCE,
            destResourceName,
            /*destDatasetName*/ null);

    // Assert resource returned in clone flight response
    assertClonedBqDataset(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        // COPY_DEFINITION doesn't make sense for referenced resources. COPY_DEFINITION was
        // converted to COPY_REFERENCE.
        ApiCloningInstructionsEnum.REFERENCE,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        sourceResource.getMetadata().getDescription(),
        /*expectedProjectId=*/ projectId,
        /*expectedDatasetName=*/ sourceDatasetName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        userAccessUtils.defaultUserAuthRequest());

    // Assert resource returned by get
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getReferencedBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertApiBqDatasetEquals(clonedResource, gotResource);
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
    mockMvcUtils.cloneControlledBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName,
        /*destDatasetName*/ null);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    assertThat(destWorkspace.getPolicies(), containsInAnyOrder(PolicyFixtures.REGION_POLICY_IOWA));
    assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deletePolicies(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  void clone_copyResource_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledBqDataset_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId2, destResourceName);
  }

  @Test
  void clone_copyReference_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledBqDataset_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceResource.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  private void assertBqDataset(
      ApiGcpBigQueryDatasetResource actualDataset,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedProjectId,
      String expectedDatasetName,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualDataset.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.BIG_QUERY_DATASET,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedProjectId, actualDataset.getAttributes().getProjectId());
    assertEquals(expectedDatasetName, actualDataset.getAttributes().getDatasetId());
  }

  private void assertClonedControlledBqDataset(
      ApiGcpBigQueryDatasetResource actualDataset,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedProjectId,
      String expectedDatasetName,
      String expectedRegion,
      String expectedCreatedBy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    assertClonedBqDataset(
        actualDataset,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        expectedProjectId,
        expectedDatasetName,
        expectedCreatedBy,
        userRequest);

    assertControlledResourceMetadata(
        actualDataset.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.SHARED_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser(),
        ApiPrivateResourceState.NOT_APPLICABLE,
        expectedRegion);
  }

  private void assertClonedBqDataset(
      ApiGcpBigQueryDatasetResource actualDataset,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedProjectId,
      String expectedDatasetName,
      String expectedCreatedBy,
      AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualDataset.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.BIG_QUERY_DATASET,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceResource.getMetadata().getResourceId(),
        expectedCreatedBy,
        userRequest);

    assertEquals(expectedProjectId, actualDataset.getAttributes().getProjectId());
    assertEquals(expectedDatasetName, actualDataset.getAttributes().getDatasetId());
  }

  /** Calls GCP directly to assert dataset location, table lifetimes. */
  private void assertBqDatasetAttributes(
      String projectId,
      String datasetId,
      String expectedLocation,
      @Nullable Long expectedDefaultTableLifetime,
      @Nullable Long expectedDefaultPartitionLifetime)
      throws Exception {
    Dataset dataset =
        crlService.createWsmSaBigQueryCow().datasets().get(projectId, datasetId).execute();
    assertEquals(expectedLocation, dataset.getLocation());
    if (expectedDefaultTableLifetime == null) {
      assertEquals(null, dataset.getDefaultTableExpirationMs());
    } else {
      assertEquals(expectedDefaultTableLifetime, dataset.getDefaultTableExpirationMs() / 1000);
    }
    if (expectedDefaultPartitionLifetime == null) {
      assertEquals(null, dataset.getDefaultPartitionExpirationMs());
    } else {
      assertEquals(
          expectedDefaultPartitionLifetime, dataset.getDefaultPartitionExpirationMs() / 1000);
    }
  }
}
