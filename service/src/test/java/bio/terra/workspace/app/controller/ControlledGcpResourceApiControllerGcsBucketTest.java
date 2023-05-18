package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.fixtures.ControlledResourceFixtures.RESOURCE_DESCRIPTION;
import static bio.terra.workspace.common.utils.MockMvcUtils.LOAD_SIGNED_URL_LIST_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.LOAD_SIGNED_URL_LIST_RESULT_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertApiGcsBucketEquals;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertControlledResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceMetadata;
import static bio.terra.workspace.common.utils.MockMvcUtils.assertResourceReady;
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
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.generated.model.ApiLoadSignedUrlListRequestBody;
import bio.terra.workspace.generated.model.ApiLoadSignedUrlListResult;
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
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketHandler;
import bio.terra.workspace.service.resource.model.StewardshipType;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.test.web.servlet.MockMvc;

/** Connected tests for controlled GCS buckets. */
// Per-class lifecycle on this test to allow a shared workspace object across tests, which saves
// time creating and deleting GCP contexts.
@Tag("connectedPlus")
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

  private static final String URL_LIST =
      "https://raw.githubusercontent.com/yuhuyoyo/test-repo/main/helloworld.tsv";

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

  private final String sourceResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String sourceBucketName = TestUtils.appendRandomNumber("source-bucket-name");
  private ApiGcpGcsBucketResource sourceBucket;

  private final String source2ResourceName = TestUtils.appendRandomNumber("source-resource-name");
  private final String source2BucketName = TestUtils.appendRandomNumber("source-bucket-name");
  private ApiGcpGcsBucketResource source2Bucket;

  // See here for how to skip workspace creation for local runs:
  // https://github.com/DataBiosphere/terra-workspace-manager#for-local-runs-skip-workspacecontext-creation
  @BeforeAll
  public void setup() throws Exception {
    // Make sure no debug info is set
    jobService.setFlightDebugInfoForTest(null);

    // To work around the permission propagation delay, I removed the per-test
    // grant and revoke calls. Instead, we use the following fixed setup:
    //
    // defaultUser - owner of both workspaces
    // secondUser - writer on workspace1, reader on workspace2
    // noBillingUser - no access on both workspaces
    //
    // We split the create workspace from the cloud context so that we can
    // do the grants and get the Sam group set before we sync the permissions
    // to the project. The owner may or may not get a temporary grant, but we need to wait
    // for propagation of the other user's permissions. So the
    // sequence is:
    //
    // 1. create workspaces as default user
    // 2. grant permissions to 2nd user
    // 3. create cloud context
    // 4. wait for 2nd user to have project permissions

    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();

    workspaceId = mockMvcUtils.createWorkspaceWithoutCloudContext(defaultUserRequest).getId();
    workspaceId2 = mockMvcUtils.createWorkspaceWithoutCloudContext(defaultUserRequest).getId();

    // Setup 2nd user
    mockMvcUtils.grantRole(
        defaultUserRequest,
        workspaceId,
        WsmIamRole.WRITER,
        userAccessUtils.secondUser().getEmail());
    mockMvcUtils.grantRole(
        defaultUserRequest,
        workspaceId2,
        WsmIamRole.READER,
        userAccessUtils.secondUser().getEmail());

    // Create the cloud contexts
    mockMvcUtils.createGcpCloudContextAndWait(defaultUserRequest, workspaceId);
    mockMvcUtils.createGcpCloudContextAndWait(defaultUserRequest, workspaceId2);

    ApiWorkspaceDescription workspace =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId);
    projectId = workspace.getGcpContext().getProjectId();

    ApiWorkspaceDescription workspace2 =
        mockMvcUtils.getWorkspace(userAccessUtils.defaultUserAuthRequest(), workspaceId2);
    projectId2 = workspace2.getGcpContext().getProjectId();

    // Wait for both users to have permission in both projects
    GcpCloudUtils.waitForProjectAccess(
        userAccessUtils.defaultUser().getGoogleCredentials(), projectId);
    GcpCloudUtils.waitForProjectAccess(
        userAccessUtils.defaultUser().getGoogleCredentials(), projectId2);
    GcpCloudUtils.waitForProjectAccess(
        userAccessUtils.secondUser().getGoogleCredentials(), projectId);
    GcpCloudUtils.waitForProjectAccess(
        userAccessUtils.secondUser().getGoogleCredentials(), projectId2);

    // It is easier to make two buckets and do clone both directions than to
    // get different permissions on users.
    sourceBucket =
        mockMvcUtils
            .createControlledGcsBucket(
                defaultUserRequest,
                workspaceId,
                sourceResourceName,
                sourceBucketName,
                LOCATION,
                STORAGE_CLASS,
                LIFECYCLE_API)
            .getGcpBucket();
    cloudUtils.addFileToBucket(
        userAccessUtils.defaultUser().getGoogleCredentials(), projectId, sourceBucketName);

    source2Bucket =
        mockMvcUtils
            .createControlledGcsBucket(
                defaultUserRequest,
                workspaceId2,
                source2ResourceName,
                source2BucketName,
                LOCATION,
                STORAGE_CLASS,
                LIFECYCLE_API)
            .getGcpBucket();
    cloudUtils.addFileToBucket(
        userAccessUtils.defaultUser().getGoogleCredentials(), projectId2, source2BucketName);
  }

  /**
   * Reset the {@link FlightDebugInfo} on the {@link JobService} to not interfere with other tests.
   */
  @AfterEach
  public void resetFlightDebugInfo() {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    jobService.setFlightDebugInfoForTest(null);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId, defaultUserRequest);
    StairwayTestUtils.enumerateJobsDump(jobService, workspaceId2, defaultUserRequest);
  }

  @AfterAll
  public void cleanup() throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    mockMvcUtils.deleteWorkspace(defaultUserRequest, workspaceId);
    mockMvcUtils.deleteWorkspace(defaultUserRequest, workspaceId2);
  }

  @Test
  public void create() throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();

    // Assert resource returned by create
    assertGcsBucket(
        sourceBucket,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        workspaceId,
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        sourceBucketName,
        /*expectedCreatedBy=*/ defaultUserRequest.getEmail(),
        /*expectedLastUpdatedBy=*/ defaultUserRequest.getEmail());

    // Assert got resource is same as created resource
    ApiGcpGcsBucketResource gotBucket =
        mockMvcUtils.getControlledGcsBucket(
            defaultUserRequest, workspaceId, sourceBucket.getMetadata().getResourceId());
    assertApiGcsBucketEquals(sourceBucket, gotBucket);
    assertResourceReady(gotBucket.getMetadata());

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        defaultUserRequest,
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        sourceBucketName);
    cloudUtils.assertBucketAttributes(
        defaultUserRequest, projectId, sourceBucketName, LOCATION, STORAGE_CLASS, LIFECYCLE_GCP);
  }

  @Test
  public void loadSignedUrlList_succeedsAndFileLoadedIntoBucket() throws Exception {
    AuthenticatedUserRequest defaultUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();

    ApiLoadSignedUrlListResult result =
        loadSignedUrlList(defaultUserRequest, sourceBucket.getMetadata().getResourceId());

    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    cloudUtils.assertBucketFiles(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        sourceBucketName,
        "raw.githubusercontent.com/yuhuyoyo/test-repo/main/helloworld.txt",
        "hello world\n");
    cloudUtils.assertBucketFiles(
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        sourceBucketName,
        "raw.githubusercontent.com/yuhuyoyo/test-repo/main/helloworld2.txt",
        "hello world again!\n");
  }

  @Test
  public void loadSignedUrlList_403() throws Exception {
    AuthenticatedUserRequest thirdUserAuthRequest = userAccessUtils.thirdUserAuthRequest();

    // Third user has no access.
    loadSignedUrlListExpectError(
        thirdUserAuthRequest, sourceBucket.getMetadata().getResourceId(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void loadSignedUrlList_404() throws Exception {
    AuthenticatedUserRequest secondUserAuthRequest = userAccessUtils.secondUserAuthRequest();

    loadSignedUrlListExpectError(
        secondUserAuthRequest, /*bucketId=*/ UUID.randomUUID(), HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void update() throws Exception {
    var newName = TestUtils.appendRandomNumber("newbucketresourcename");
    var newDescription = "This is an updated description";
    var newCloningInstruction = ApiCloningInstructionsEnum.REFERENCE;

    AuthenticatedUserRequest ownerUserRequest =
        userAccessUtils.defaultUser().getAuthenticatedRequest();
    AuthenticatedUserRequest writerUserRequest =
        userAccessUtils.secondUser().getAuthenticatedRequest();

    ApiGcpGcsBucketResource updatedResource =
        mockMvcUtils.updateControlledGcsBucket(
            writerUserRequest,
            workspaceId,
            sourceBucket.getMetadata().getResourceId(),
            newName,
            newDescription,
            newCloningInstruction);

    ApiGcpGcsBucketResource getResource =
        mockMvcUtils.getControlledGcsBucket(
            ownerUserRequest, workspaceId, sourceBucket.getMetadata().getResourceId());
    assertEquals(updatedResource, getResource);
    assertGcsBucket(
        getResource,
        ApiStewardshipType.CONTROLLED,
        newCloningInstruction,
        workspaceId,
        newName,
        newDescription,
        sourceBucketName,
        ownerUserRequest.getEmail(),
        writerUserRequest.getEmail());
    mockMvcUtils.updateControlledGcsBucket(
        ownerUserRequest,
        workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        sourceResourceName,
        RESOURCE_DESCRIPTION,
        ApiCloningInstructionsEnum.DEFINITION);
  }

  @Test
  public void clone_requesterNoReadAccessOnSourceWorkspace_throws403() throws Exception {
    mockMvcUtils.cloneControlledGcsBucketAsync(
        userAccessUtils.noBillingUser().getAuthenticatedRequest(),
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
    mockMvcUtils.cloneControlledGcsBucketAsync(
        userAccessUtils.secondUser().getAuthenticatedRequest(),
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
  public void clone_userWithWriteAccessOnDestWorkspace_succeeds() throws Exception {
    var destResourceName = TestUtils.appendRandomNumber("clonedbucket");
    AuthenticatedUserRequest userRequest = userAccessUtils.secondUser().getAuthenticatedRequest();
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userRequest,
                /*sourceWorkspaceId=*/ workspaceId2,
                source2Bucket.getMetadata().getResourceId(),
                /*destWorkspaceId=*/ workspaceId,
                ApiCloningInstructionsEnum.RESOURCE,
                destResourceName,
                /*destBucketName=*/ null,
                /*destLocation*/ null)
            .getGcpBucket();

    // Assert resource returned in clone flight response
    assertClonedControlledGcsBucket(
        clonedResource,
        workspaceId,
        destResourceName,
        source2Bucket.getMetadata().getDescription(),
        ControlledGcsBucketHandler.getHandler()
            .generateCloudName(workspaceId, "cloned-" + destResourceName),
        LOCATION,
        userRequest.getEmail(),
        workspaceId2,
        source2Bucket.getMetadata().getResourceId(),
        userRequest);
  }

  // Tests getUniquenessCheckAttributes() works
  @Test
  void clone_duplicateBucketName_jobThrows409() throws Exception {
    ApiErrorReport errorReport =
        mockMvcUtils.cloneControlledGcsBucket_jobError(
            userAccessUtils.defaultUser().getAuthenticatedRequest(),
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    ApiCreatedControlledGcpGcsBucket clonedResource =
        mockMvcUtils.cloneControlledGcsBucket(
            userRequest,
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
    mockMvcUtils.assertNoResourceWithName(userRequest, workspaceId, destResourceName);
  }

  @Test
  public void clone_copyDefinition() throws Exception {
    // Source resource is in us-west4

    // Clone resource to europe-west1
    // Note to resource authors: Set all request fields, eg BQ dataset location.
    String destLocation = "europe-west1";
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    String destBucketName = TestUtils.appendRandomNumber("dest-bucket-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userRequest,
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
        sourceBucket.getMetadata().getDescription(),
        destBucketName,
        /*expectedCreatedBy=*/ userAccessUtils.getDefaultUserEmail(),
        workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userRequest, workspaceId2, clonedResource.getMetadata().getResourceId());
    assertApiGcsBucketEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketHasNoFiles(
        userRequest, projectId2, gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userRequest,
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userRequest,
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
        sourceBucket.getMetadata().getDescription(),
        destBucketName,
        destLocation,
        /*expectedCreatedBy=*/ userAccessUtils.defaultUser().getEmail(),
        workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userRequest, workspaceId, clonedResource.getMetadata().getResourceId());
    assertApiGcsBucketEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        userRequest,
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userRequest,
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userRequest,
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
        sourceBucket.getMetadata().getDescription(),
        destBucketName,
        destLocation,
        /*expectedCreatedBy=*/ userAccessUtils.defaultUser().getEmail(),
        workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getControlledGcsBucket(
            userRequest, workspaceId2, clonedResource.getMetadata().getResourceId());
    assertApiGcsBucketEquals(clonedResource, gotResource);

    // Call GCP directly.
    cloudUtils.assertBucketFiles(
        userRequest,
        userAccessUtils.defaultUser().getGoogleCredentials(),
        projectId,
        gotResource.getAttributes().getBucketName());
    cloudUtils.assertBucketAttributes(
        userRequest,
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
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();
    ApiGcpGcsBucketResource clonedResource =
        mockMvcUtils
            .cloneControlledGcsBucket(
                userRequest,
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
        sourceBucket.getMetadata().getDescription(),
        sourceBucketName,
        /*expectedCreatedBy=*/ userAccessUtils.defaultUser().getEmail(),
        workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        userRequest);

    // Assert resource returned by get
    ApiGcpGcsBucketResource gotResource =
        mockMvcUtils.getReferencedGcsBucket(
            userRequest, workspaceId, clonedResource.getMetadata().getResourceId());
    assertApiGcsBucketEquals(clonedResource, gotResource);
  }

  // Destination workspace policy is the merge of source workspace policy and pre-clone destination
  // workspace policy
  @Test
  @EnabledIf(expression = "${feature.tps-enabled}", loadContext = true)
  void clone_policiesMerged() throws Exception {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    // Clean up policies from previous runs, if any exist
    mockMvcUtils.deletePolicies(userRequest, workspaceId);
    mockMvcUtils.deletePolicies(userRequest, workspaceId2);

    // Add broader region policy to destination, narrow policy on source.
    mockMvcUtils.updatePolicies(
        userRequest,
        workspaceId,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_NEVADA),
        /*policiesToRemove=*/ null);
    mockMvcUtils.updatePolicies(
        userRequest,
        workspaceId2,
        /*policiesToAdd=*/ ImmutableList.of(PolicyFixtures.REGION_POLICY_USA),
        /*policiesToRemove=*/ null);

    // Clone resource
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    mockMvcUtils.cloneControlledGcsBucket(
        userRequest,
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName,
        /*destBucketName*/ null,
        /*destLocation*/ null);

    // Assert dest workspace policy is reduced to the narrower region.
    ApiWorkspaceDescription destWorkspace = mockMvcUtils.getWorkspace(userRequest, workspaceId2);
    assertThat(
        destWorkspace.getPolicies(), containsInAnyOrder(PolicyFixtures.REGION_POLICY_NEVADA));
    Assertions.assertFalse(destWorkspace.getPolicies().contains(PolicyFixtures.REGION_POLICY_USA));

    // Clean up: Delete policies
    mockMvcUtils.deletePolicies(userRequest, workspaceId);
    mockMvcUtils.deletePolicies(userRequest, workspaceId2);
  }

  @Test
  void clone_copyResource_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    mockMvcUtils.cloneControlledGcsBucket_undo(
        userRequest,
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.RESOURCE,
        destResourceName);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(userRequest, workspaceId2, destResourceName);
  }

  @Test
  void clone_copyReference_undo() throws Exception {
    String destResourceName = TestUtils.appendRandomNumber("dest-resource-name");
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUser().getAuthenticatedRequest();

    mockMvcUtils.cloneControlledGcsBucket_undo(
        userRequest,
        /*sourceWorkspaceId=*/ workspaceId,
        sourceBucket.getMetadata().getResourceId(),
        /*destWorkspaceId=*/ workspaceId2,
        ApiCloningInstructionsEnum.REFERENCE,
        destResourceName);

    // Assert clone doesn't exist. There's no resource ID, so search on resource name.
    mockMvcUtils.assertNoResourceWithName(userRequest, workspaceId2, destResourceName);
  }

  private void assertGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
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
        expectedResourceDescription,
        /*expectedResourceLineage=*/ new ApiResourceLineage(),
        expectedCreatedBy,
        expectedLastUpdatedBy);
    assertResourceReady(actualBucket.getMetadata());
    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
  }

  public void assertClonedControlledGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedBucketName,
      String expectedRegion,
      String expectedCreatedBy,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      AuthenticatedUserRequest cloneUserRequest)
      throws InterruptedException {
    assertClonedGcsBucket(
        actualBucket,
        ApiStewardshipType.CONTROLLED,
        ApiCloningInstructionsEnum.DEFINITION,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        expectedBucketName,
        expectedCreatedBy,
        sourceWorkspaceId,
        sourceResourceId,
        cloneUserRequest);
    assertControlledResourceMetadata(
        actualBucket.getMetadata().getControlledResourceMetadata(),
        ApiAccessScope.SHARED_ACCESS,
        ApiManagedBy.USER,
        new ApiPrivateResourceUser(),
        ApiPrivateResourceState.NOT_APPLICABLE,
        expectedRegion);
    assertResourceReady(actualBucket.getMetadata());
  }

  private void assertClonedGcsBucket(
      ApiGcpGcsBucketResource actualBucket,
      ApiStewardshipType expectedStewardshipType,
      ApiCloningInstructionsEnum expectedCloningInstructions,
      UUID expectedWorkspaceId,
      String expectedResourceName,
      String expectedResourceDescription,
      String expectedBucketName,
      String expectedCreatedBy,
      UUID sourceWorkspaceId,
      UUID sourceResourceId,
      AuthenticatedUserRequest cloneUserRequest)
      throws InterruptedException {
    mockMvcUtils.assertClonedResourceMetadata(
        actualBucket.getMetadata(),
        ApiCloudPlatform.GCP,
        ApiResourceType.GCS_BUCKET,
        expectedStewardshipType,
        expectedCloningInstructions,
        expectedWorkspaceId,
        expectedResourceName,
        expectedResourceDescription,
        sourceWorkspaceId,
        sourceResourceId,
        expectedCreatedBy,
        StewardshipType.CONTROLLED,
        cloneUserRequest);
    assertResourceReady(actualBucket.getMetadata());
    assertEquals(expectedBucketName, actualBucket.getAttributes().getBucketName());
  }

  private void loadSignedUrlListExpectError(
      AuthenticatedUserRequest userRequest, UUID bucketId, int httpStatus) throws Exception {
    ApiLoadSignedUrlListRequestBody requestBody =
        new ApiLoadSignedUrlListRequestBody().urlList(URL_LIST);
    mockMvcUtils.postExpect(
        userRequest,
        objectMapper.writeValueAsString(requestBody),
        String.format(LOAD_SIGNED_URL_LIST_PATH_FORMAT, workspaceId, bucketId),
        httpStatus);
  }

  private ApiLoadSignedUrlListResult loadSignedUrlList(
      AuthenticatedUserRequest userRequest, UUID bucketId) throws Exception {
    ApiLoadSignedUrlListRequestBody requestBody =
        new ApiLoadSignedUrlListRequestBody().urlList(URL_LIST);
    var serializedResponse =
        mockMvcUtils.getSerializedResponseForPost(
            userRequest,
            String.format(LOAD_SIGNED_URL_LIST_PATH_FORMAT, workspaceId, bucketId),
            objectMapper.writeValueAsString(requestBody));
    var result = objectMapper.readValue(serializedResponse, ApiLoadSignedUrlListResult.class);
    String jobId = result.getJobReport().getId();
    while (StairwayTestUtils.jobIsRunning(result.getJobReport())) {
      Thread.sleep(/*millis=*/ 5000);
      result =
          getLoadSignedUrlListResult(
              userRequest, workspaceId, sourceBucket.getMetadata().getResourceId(), jobId);
    }
    assertEquals(StatusEnum.SUCCEEDED, result.getJobReport().getStatus());
    return result;
  }

  private ApiLoadSignedUrlListResult getLoadSignedUrlListResult(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId, String jobId)
      throws Exception {
    String serializedResponse =
        mockMvcUtils.getSerializedResponseForGetJobResult(
            userRequest,
            String.format(LOAD_SIGNED_URL_LIST_RESULT_PATH_FORMAT, workspaceId, resourceId, jobId));
    return objectMapper.readValue(serializedResponse, ApiLoadSignedUrlListResult.class);
  }
}
