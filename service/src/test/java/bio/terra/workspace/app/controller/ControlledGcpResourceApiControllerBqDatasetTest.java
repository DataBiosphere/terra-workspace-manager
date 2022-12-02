package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.GcpCloudUtils;
import bio.terra.workspace.common.StairwayTestUtils;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.PolicyFixtures;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.common.utils.TestUtils;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpBigQueryDataset;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceLineageEntry;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.workspace.model.WsmObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerBqDatasetTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledGcpResourceApiControllerBqDatasetTest.class);

  @Autowired MockMvc mockMvc;
  @Autowired MockMvcUtils mockMvcUtils;
  @Autowired ObjectMapper objectMapper;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired JobService jobService;
  @Autowired GcpCloudUtils cloudUtils;
  @Autowired FeatureConfiguration features;
  @Autowired CrlService crlService;
  @Autowired WorkspaceActivityLogService workspaceActivityLogService;

  private UUID workspaceId;
  private UUID workspaceId2;
  private ApiGcpBigQueryDatasetResource sourceControlledDataset;

  @BeforeAll
  public void setup() throws Exception {
    workspaceId =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();
    workspaceId2 =
        mockMvcUtils
            .createWorkspaceWithCloudContext(userAccessUtils.defaultUserAuthRequest())
            .getId();

    sourceControlledDataset =
        mockMvcUtils
            .createControlledBqDataset(userAccessUtils.defaultUserAuthRequest(), workspaceId)
            .getBigQueryDataset();
    cloudUtils.populateBqTable(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        sourceControlledDataset.getAttributes().getProjectId(),
        sourceControlledDataset.getAttributes().getDatasetId());
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId, userAccessUtils.defaultUserAuthRequest());
    StairwayTestUtils.enumerateJobsDump(
        jobService, workspaceId2, userAccessUtils.defaultUserAuthRequest());
    jobService.setFlightDebugInfoForTest(null);
  }

  @AfterAll
  public void cleanup() throws Exception {
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    mockMvcUtils.deleteWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
  }

  @Test
  public void create() throws Exception {
    ApiCreatedControlledGcpBigQueryDataset bqDataset =
        mockMvcUtils.createControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId);

    ApiGcpBigQueryDatasetResource actualBqDataset =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, bqDataset.getResourceId());
    ApiGcpBigQueryDatasetResource expectedBqDataset = bqDataset.getBigQueryDataset();

    assertResourceMetadata(expectedBqDataset.getMetadata(), actualBqDataset.getMetadata());
    assertEquals(
        expectedBqDataset.getAttributes().getDatasetId(),
        actualBqDataset.getAttributes().getDatasetId());
    assertEquals(
        expectedBqDataset.getAttributes().getProjectId(),
        actualBqDataset.getAttributes().getProjectId());
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId).get();
    assertEquals(userAccessUtils.getDefaultUserEmail(), changeDetails.actorEmail());
    assertEquals(
        actualBqDataset.getMetadata().getResourceId().toString(), changeDetails.changeSubjectId());
    assertEquals(WsmObjectType.RESOURCE, changeDetails.changeSubjectType());
    assertNotNull(changeDetails.changeDate());
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId2).get();

    mockMvcUtils.cloneControlledBqDatasetAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceControlledDataset.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destDatasetName=*/ null,
        HttpStatus.SC_FORBIDDEN,
        /*shouldUndo=*/ false);
    assertEquals(
        changeDetails, workspaceActivityLogService.getLastUpdatedDetails(workspaceId2).get());
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
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId2).get();

    mockMvcUtils.cloneControlledBqDatasetAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceControlledDataset.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destDatasetName=*/ null,
        HttpStatus.SC_FORBIDDEN,
        /*shouldUndo=*/ false);

    assertEquals(
        changeDetails, workspaceActivityLogService.getLastUpdatedDetails(workspaceId2).get());
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
  void clone_duplicateDatasetName_jobThrows409() throws Exception {
    ActivityLogChangeDetails changeDetails =
        workspaceActivityLogService.getLastUpdatedDetails(workspaceId).get();

    ApiErrorReport errorReport =
        mockMvcUtils.cloneControlledBqDataset_jobError(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceControlledDataset.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.RESOURCE,
            /*destResourceName=*/ null,
            /*destDatasetName=*/ sourceControlledDataset.getAttributes().getDatasetId(),
            HttpStatus.SC_CONFLICT);

    assertEquals(
        changeDetails, workspaceActivityLogService.getLastUpdatedDetails(workspaceId).get());
    assertThat(
        errorReport.getMessage(), equalTo("A resource with matching attributes already exists"));
  }

  @Test
  void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceControlledDataset.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId,
        ApiCloningInstructionsEnum.NOTHING,
        destResourceName,
        /*destDatasetName=*/ null);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  @Test
  void clone_copyDefinition() throws Exception {
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceControlledDataset.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.DEFINITION,
            destResourceName,
            /*destDatasetName=*/ null);

    // Assert resource returned in clone flight response
    assertClonedBqDataset(
        clonedResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        sourceControlledDataset.getAttributes().getDatasetId());

    // Assert resource returned by controlledResourceService.getControlledResource()
    final UUID destResourceId = clonedResource.getMetadata().getResourceId();
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId2, destResourceId);
    assertClonedBqDataset(
        gotResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        sourceControlledDataset.getAttributes().getDatasetId());

    // Call GCP directly. Assert dest BQ dataset exists and has no tables.
    cloudUtils.assertDatasetHasNoTables(
        userAccessUtils.defaultUserAuthRequest(),
        gotResource.getAttributes().getProjectId(),
        gotResource.getAttributes().getDatasetId());
  }

  // Note to resource authors: Set all per-resource request fields. For example, for cloning BQ
  // dataset, set location.
  @Test
  void clone_copyResource_sameWorkspace() throws Exception {
    // Source resource is in default location, us-central1

    // Clone resource to europe-west1
    // TODO(PF-2184): Set location to europe-west1 after PF-2184 is fixed.
    // String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDatasetName =
        sourceControlledDataset.getAttributes().getDatasetId() + "_copyResource_sameWorkspace";
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceControlledDataset.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourceName,
            destDatasetName);
    // destDatasetName,
    // destLocation);

    // Assert resource returned in clone flight response
    assertClonedBqDataset(
        clonedResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        destDatasetName);

    // Assert resource returned by controlledResourceService.getControlledResource()
    final UUID destResourceId = clonedResource.getMetadata().getResourceId();
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceId);
    assertClonedBqDataset(
        gotResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        destDatasetName);

    // Call GCP directly
    // Assert dest dataset has correct location
    // TODO(PF-2184): Uncomment after PF-2184 is fixed
    // assertBqDatasetLocation(destLocation, destProjectId, destDatasetId);
    // Assert dest dataset has correct contents
    cloudUtils.assertBqTableContents(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        gotResource.getAttributes().getProjectId(),
        gotResource.getAttributes().getDatasetId());
  }

  // Note to resource authors: Set all per-resource request fields. For example, for cloning BQ
  // dataset, set location.
  @Test
  void clone_copyResource_differentWorkspace() throws Exception {
    // Source resource is in default location, us-central1

    // Clone resource to europe-west1
    // TODO(PF-2184): Set location to europe-west1 after PF-2184 is fixed.
    // String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destDatasetName =
        sourceControlledDataset.getAttributes().getDatasetId() + "_copyResource_differentWorkspace";
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceControlledDataset.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId2,
            ApiCloningInstructionsEnum.RESOURCE,
            destResourceName,
            destDatasetName);
    // destDatasetName,
    // destLocation);

    // Assert resource returned in clone flight response
    assertClonedBqDataset(
        clonedResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        destDatasetName);

    // Assert resource returned by controlledResourceService.getControlledResource()
    final UUID destResourceId = clonedResource.getMetadata().getResourceId();
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId2, destResourceId);
    assertClonedBqDataset(
        gotResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        /*expectedDestWorkspaceId=*/ workspaceId2,
        destResourceName,
        destDatasetName);

    // Call GCP directly
    // Assert dest dataset has correct location
    // TODO(PF-2184): Uncomment after PF-2184 is fixed
    // assertBqDatasetLocation(destLocation, destProjectId, destDatasetId);
    // Assert dest dataset has correct contents
    cloudUtils.assertBqTableContents(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        gotResource.getAttributes().getProjectId(),
        gotResource.getAttributes().getDatasetId());
  }

  @Test
  void clone_copyReference() throws Exception {
    // Note - Source resource is COPY_DEFINITION
    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpBigQueryDatasetResource clonedResource =
        mockMvcUtils.cloneControlledBqDataset(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceControlledDataset.getMetadata().getResourceId(),
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
        sourceControlledDataset.getAttributes().getDatasetId());

    // Assert resource returned by referencedResourceService.getReferenceResource()
    final UUID destResourceId = clonedResource.getMetadata().getResourceId();
    final ApiGcpBigQueryDatasetResource gotResource =
        mockMvcUtils.getReferencedBqDataset(
            userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceId);
    assertClonedBqDataset(
        gotResource,
        ApiStewardshipType.REFERENCED,
        // COPY_DEFINITION doesn't make sense for referenced resources. COPY_DEFINITION was
        // converted to COPY_REFERENCE.
        ApiCloningInstructionsEnum.REFERENCE,
        /*expectedDestWorkspaceId=*/ workspaceId,
        destResourceName,
        sourceControlledDataset.getAttributes().getDatasetId());
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
    mockMvcUtils.cloneControlledBqDataset(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceControlledDataset.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName,
        /*destDatasetName*/ null);

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

  @Test
  void clone_copyResource_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledBqDataset_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceControlledDataset.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        destResourceName,
        ApiCloningInstructionsEnum.RESOURCE);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId2, destResourceName);
  }

  @Test
  void clone_copyReference_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledBqDataset_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceControlledDataset.getMetadata().getResourceId(),
        // clone_copyResource_undo tested cloning to different workspace. Have
        // this test clone to same workspace, for variety.
        /*destWorkspaceId=*/ workspaceId,
        destResourceName,
        ApiCloningInstructionsEnum.REFERENCE);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  private void assertClonedBqDataset(
      ApiGcpBigQueryDatasetResource actualDataset,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedDatasetName) {
    ApiResourceMetadata actualMetadata = actualDataset.getMetadata();
    assertEquals(expectedWorkspaceId, actualMetadata.getWorkspaceId());
    assertEquals(expectedResourceName, actualMetadata.getName());
    assertEquals(RESOURCE_DESCRIPTION, actualMetadata.getDescription());
    assertEquals(ApiResourceType.BIG_QUERY_DATASET, actualMetadata.getResourceType());
    assertEquals(expectedStewardshipType, actualMetadata.getStewardshipType());
    assertEquals(ApiCloudPlatform.GCP, actualMetadata.getCloudPlatform());
    assertEquals(expectedCloningInstructions, actualMetadata.getCloningInstructions());

    ApiResourceLineage expectedResourceLineage = new ApiResourceLineage();
    expectedResourceLineage.add(
        new ApiResourceLineageEntry()
            .sourceWorkspaceId(workspaceId)
            .sourceResourceId(sourceControlledDataset.getMetadata().getResourceId()));
    assertEquals(expectedResourceLineage, actualMetadata.getResourceLineage());

    assertEquals(
        PropertiesUtils.convertMapToApiProperties(
            ControlledResourceFixtures.DEFAULT_RESOURCE_PROPERTIES),
        actualMetadata.getProperties());

    assertEquals(expectedDatasetName, actualDataset.getAttributes().getDatasetId());
  }

  private static void assertResourceMetadata(
      ApiResourceMetadata expectedMetadata, ApiResourceMetadata actualMetadata) {
    assertEquals(expectedMetadata.getName(), actualMetadata.getName());
    assertEquals(expectedMetadata.getDescription(), actualMetadata.getDescription());
    assertEquals(
        expectedMetadata.getCloningInstructions(), actualMetadata.getCloningInstructions());
    assertEquals(expectedMetadata.getStewardshipType(), actualMetadata.getStewardshipType());
    assertEquals(expectedMetadata.getResourceType(), actualMetadata.getResourceType());
    assertEquals(expectedMetadata.getProperties(), actualMetadata.getProperties());
    assertEquals(expectedMetadata.getCreatedDate(), actualMetadata.getCreatedDate());
    assertEquals(expectedMetadata.getCreatedBy(), actualMetadata.getCreatedBy());
  }

  private void assertNoResourceWithName(
      AuthenticatedUserRequest userRequest, UUID workspaceId, String unexpectedResourceName)
      throws Exception {
    mockMvcUtils
        .enumerateResources(userRequest, workspaceId)
        .forEach(
            actualResource ->
                assertNotEquals(unexpectedResourceName, actualResource.getMetadata().getName()));
  }
}
