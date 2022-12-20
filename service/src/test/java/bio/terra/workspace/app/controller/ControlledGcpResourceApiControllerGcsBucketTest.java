package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.assertClonedResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
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
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketResource;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.generated.model.ApiPrivateResourceState;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceLineage;
import bio.terra.workspace.generated.model.ApiResourceType;
import bio.terra.workspace.generated.model.ApiStewardshipType;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.common.collect.ImmutableList;
import java.util.List;
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

/** Connected tests for controlled GCS buckets. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@TestInstance(Lifecycle.PER_CLASS)
public class ControlledGcpResourceApiControllerGcsBucketTest extends BaseConnectedTest {
  private static final Logger logger =
      LoggerFactory.getLogger(ControlledGcpResourceApiControllerGcsBucketTest.class);

  // GCP default is us-central1. Use something different, so we know this is copied to clone
  // correctly.
  private static final String LOCATION = "us-west4";
  // GCS default is STANDARD. Use something different, so we know this is copied to clone correctly.
  private static final ApiGcpGcsBucketDefaultStorageClass STORAGE_CLASS =
      ApiGcpGcsBucketDefaultStorageClass.NEARLINE;
  // GCP default is to not set this. Set this, so we know this is copied to clone correctly.
  private static final ApiGcpGcsBucketLifecycle LIFECYCLE_API =
      new ApiGcpGcsBucketLifecycle()
          .addRulesItem(
              new ApiGcpGcsBucketLifecycleRule()
                  .action(
                      new ApiGcpGcsBucketLifecycleRuleAction()
                          .type(ApiGcpGcsBucketLifecycleRuleActionType.DELETE))
                  .condition(new ApiGcpGcsBucketLifecycleRuleCondition().age(3)));
  // GCP client library version of LIFECYCLE_API
  private static final List<LifecycleRule> LIFECYCLE_GCP =
      ImmutableList.of(
          new LifecycleRule(
              LifecycleAction.newDeleteAction(),
              LifecycleCondition.newBuilder().setAge(3).build()));

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

  private UUID workspaceId;
  private String projectId;
  private UUID workspaceId2;
  private String projectId2;

  private String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");
  private ApiGcpGcsBucketResource sourceBucket;

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

    sourceBucket =
        mockMvcUtils
            .createControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                workspaceId,
                sourceResourceName,
                sourceBucketName,
                LOCATION,
                STORAGE_CLASS,
                LIFECYCLE_API)
            .getGcpBucket();
    cloudUtils.addFileToBucket(
        userAccessUtils.defaultUser().getGoogleCredentials(), projectId, sourceBucketName);
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

  public void create() throws Exception {
    // Assert resource returned by create
    assertGcsBucket(
        sourceBucket,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        sourceResourceName,
        sourceBucketName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert got resource is same as created resource
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            sourceBucket.getMetadata().getResourceId());
    assertEquals(sourceBucket, gotBucket);

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        userAccessUtils.defaultUserAuthRequest(),
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        sourceBucketName);
    cloudUtils.assertBucketAttributes(
        userAccessUtils.defaultUserAuthRequest(),
        projectId,
        sourceBucketName,
        LOCATION,
        STORAGE_CLASS,
        LIFECYCLE_GCP);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneControlledGcsBucketAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destBucketName=*/ null,
        /*destLocation*/ null,
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

    mockMvcUtils.cloneControlledGcsBucketAsync(
        userAccessUtils.secondUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        /*destResourceName=*/ null,
        /*destBucketName=*/ null,
        /*destLocation*/ null,
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
  public void clone_secondUserWithWriteAccessOnDestWorkspace_succeeds() throws Exception {
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

    var destResourceName = TestUtils.appendRandomNumber("clonedbucket");
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userAccessUtils.secondUserAuthRequest(),
                /*sourceWorkspaceId=*/ workspaceId,
                sourceBucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId2,
                ApiCloningInstructionsEnum.RESOURCE,
                destResourceName,
                /*destBucketName=*/ null,
                /*destLocation*/ null)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedControlledGcsBucket(
        clonedResource,
        workspaceId2,
        destResourceName,
        ControlledGcsBucketHandler.getHandler()
            .generateCloudName(workspaceId2, "cloned-" + destResourceName),
        LOCATION,
        /*expectedCreatedBy=*/ userAccessUtils.getSecondUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getSecondUserEmail());

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

  // Tests getUniquenessCheckAttributes() works
  @Test
  void clone_duplicateBucketName_jobThrows409() throws Exception {
    ApiErrorReport errorReport =
        mockMvcUtils.cloneControlledGcsBucket_jobError(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceBucket.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.RESOURCE,
            /*destBucketName=*/ sourceBucket.getAttributes().getBucketName(),
            HttpStatus.SC_CONFLICT);
    assertThat(
        errorReport.getMessage(), equalTo("A resource with matching attributes already exists"));
  }

  @Test
  public void clone_copyNothing() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiCreatedControlledGcpGcsBucket clonedResource =
        mockMvcUtils.cloneControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            /*sourceWorkspaceId=*/ workspaceId,
            sourceBucket.getMetadata().getResourceId(),
            /*destWorkspaceId=*/ workspaceId,
            ApiCloningInstructionsEnum.NOTHING,
            destResourceName,
            /*destBucketName=*/ null,
            /*destLocation*/ null);

    // Assert clone result has no resource
    assertNull(clonedResource);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId, destResourceName);
  }

  @Test
  public void clone_copyDefinition() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destBucketName = TestUtils.appendRandomNumber("dest-bucket-name");
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                /*sourceWorkspaceId=*/ workspaceId,
                sourceBucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId2,
                ApiCloningInstructionsEnum.DEFINITION,
                destResourceName,
                destBucketName,
                destLocation)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedGcsBucket(
        clonedResource,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId2,
        destResourceName,
        destBucketName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketHasNoFiles(
        userAccessUtils.defaultUserAuthRequest(),
        projectId2,
        gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userAccessUtils.defaultUserAuthRequest(),
        projectId2,
        gotResource.getAttributes().getBucketName(),
        destLocation,
        STORAGE_CLASS,
        LIFECYCLE_GCP);
  }

  @Test
  void clone_copyResource_sameWorkspace() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destBucketName = TestUtils.appendRandomNumber("dest-bucket-name");
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                /*sourceWorkspaceId=*/ workspaceId,
                sourceBucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId,
                ApiCloningInstructionsEnum.RESOURCE,
                destResourceName,
                destBucketName,
                destLocation)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedControlledGcsBucket(
        clonedResource,
        workspaceId,
        destResourceName,
        destBucketName,
        destLocation,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        userAccessUtils.defaultUserAuthRequest(),
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userAccessUtils.defaultUserAuthRequest(),
        projectId,
        gotResource.getAttributes().getBucketName(),
        destLocation,
        STORAGE_CLASS,
        LIFECYCLE_GCP);
  }

  @Test
  void clone_copyResource_differentWorkspace() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destBucketName = TestUtils.appendRandomNumber("dest-bucket-name");
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                /*sourceWorkspaceId=*/ workspaceId,
                sourceBucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId2,
                ApiCloningInstructionsEnum.RESOURCE,
                destResourceName,
                destBucketName,
                destLocation)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedControlledGcsBucket(
        clonedResource,
        workspaceId2,
        destResourceName,
        destBucketName,
        destLocation,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId2,
            clonedResource.getMetadata().getResourceId());
    assertEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        userAccessUtils.defaultUserAuthRequest(),
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userAccessUtils.defaultUserAuthRequest(),
        projectId,
        gotResource.getAttributes().getBucketName(),
        destLocation,
        STORAGE_CLASS,
        LIFECYCLE_GCP);
  }

  @Test
  void clone_copyReference() throws Exception {
    // Source resource is COPY_DEFINITION

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userAccessUtils.defaultUserAuthRequest(),
                /*sourceWorkspaceId=*/ workspaceId,
                sourceBucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId,
                ApiCloningInstructionsEnum.REFERENCE,
                destResourceName,
                /*destBucketName=*/ null,
                /*destLocation=*/ null)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedGcsBucket(
        clonedResource,
        ApiStewardshipType.REFERENCED,
        // COPY_DEFINITION doesn't make sense for referenced resources. COPY_DEFINITION was
        // converted to COPY_REFERENCE.
        ApiCloningInstructionsEnum.REFERENCE,
        workspaceId,
        destResourceName,
        sourceBucketName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        /*expectedLastUpdatedBy=*/ userAccessUtils.getDefaultUserEmail());

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getReferencedGcsBucket(
            userAccessUtils.defaultUserAuthRequest(),
            workspaceId,
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
    mockMvcUtils.cloneControlledGcsBucket(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName,
        /*destBucketName*/ null,
        /*destLocation*/ null);

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
    mockMvcUtils.cloneControlledGcsBucket_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
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
    mockMvcUtils.cloneControlledGcsBucket_undo(
        userAccessUtils.defaultUserAuthRequest(),
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(
        userAccessUtils.defaultUserAuthRequest(), workspaceId2, destResourceName);
  }

  private void assertGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedBucketName,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertResourceMetadata(
        actualBucket.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.GCS_BUCKET,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
  }

  public void assertClonedControlledGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedBucketName,
      String expectedRegion,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertClonedGcsBucket(
        actualBucket,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        expectedWorkspaceId,
        expectedResourceName,
        expectedBucketName,
        expectedCreatedBy,
        expectedLastUpdatedBy);
    mockMvcUtils.assertControlledResourceMetadata(
        actualBucket.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.SHARED_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser(),
        ApiPrivateResourceState.NOT_APPLICABLE,
        expectedRegion);
  }

  private void assertClonedGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedBucketName,
      String expectedCreatedBy,
      String expectedLastUpdatedBy) {
    assertClonedResourceMetadata(
        actualBucket.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.GCS_BUCKET,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        /*sourceWorkspaceId=*/ workspaceId,
        /*sourceResourceId=*/ sourceBucket.getMetadata().getResourceId(),
        expectedCreatedBy,
        expectedLastUpdatedBy);

    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
  }
}
