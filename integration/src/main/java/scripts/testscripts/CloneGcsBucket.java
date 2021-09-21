package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_CONTENT;
import static scripts.utils.GcsBucketTestFixtures.GCS_BLOB_NAME;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULE_1_CONDITION_AGE;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULE_1_CONDITION_LIVE;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketResult;
import bio.terra.workspace.model.ClonedControlledGcpGcsBucket;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneGcsBucket extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneGcsBucket.class);

  private ControlledGcpResourceApi cloningUserResourceApi;
  private CreatedControlledGcpGcsBucket sourceBucket;
  private String destinationProjectId;
  private String nameSuffix;
  private String sourceBucketName;
  private String sourceProjectId;
  private String sourceResourceName;
  private TestUserSpecification cloningUser;
  private UUID destinationWorkspaceId;

  /**
   *
   * @param testUsers - test user configurations
   * @param sourceOwnerWorkspaceApi - API with workspace methods for first listed user (sourceOwnerUser)
   * @throws Exception
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    assertThat(testUsers, hasSize(2));
    // user creating the source resource
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // user cloning the bucket resource
    cloningUser = testUsers.get(1);

    // Create the source cloud context
    sourceProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // Create a source bucket
    final ControlledGcpResourceApi sourceOwnerResourceApi = ClientTestUtils
        .getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // create source bucket
    nameSuffix = UUID.randomUUID().toString();
    sourceResourceName = RESOURCE_PREFIX + nameSuffix;
    sourceBucket = makeControlledGcsBucketUserShared(sourceOwnerResourceApi, getWorkspaceId(), sourceResourceName, CloningInstructionsEnum.NOTHING);
    sourceBucketName = sourceBucket.getGcpBucket().getAttributes().getBucketName();

    // Make the cloning user a reader on the existing workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail), getWorkspaceId(), IamRole.READER);

    // populate source bucket
    final Storage sourceOwnerStorageClient = ClientTestUtils.getGcpStorageClient(sourceOwnerUser, sourceProjectId);
    final BlobId blobId = BlobId.of(sourceBucketName, GCS_BLOB_NAME);
    final BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    final Blob createdFile = sourceOwnerStorageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes());
    logger.info("Wrote blob {} to bucket", createdFile.getBlobId());

    // create destination workspace
    final WorkspaceApi cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
    destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace createdDestinationWorkspace = cloningUserWorkspaceApi.createWorkspace(requestBody);
    assertThat(createdDestinationWorkspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    destinationProjectId = CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, cloningUserWorkspaceApi);
    logger.info("Created destination project {} in workspace {}", destinationProjectId, destinationWorkspaceId);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi unused)
      throws Exception {
    final String destinationBucketName = "clone-" + nameSuffix;
    // clone the bucket
    final String clonedBucketDescription = "A cloned bucket";
    final CloneControlledGcpGcsBucketRequest cloneRequest = new CloneControlledGcpGcsBucketRequest()
        .bucketName(destinationBucketName)
        .destinationWorkspaceId(destinationWorkspaceId)
        .name(sourceResourceName)
        .description(clonedBucketDescription)
        .location(null) // use same as src
        .cloningInstructions(CloningInstructionsEnum.RESOURCE)
        .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    logger.info("Cloning bucket\n\tname: {}\n\tresource ID: {}\n\tworkspace: {}\n\t"
            + "projectID: {}\ninto destination bucket\n\tname: {}\n\tworkspace: {}\n\tprojectID: {}",
        sourceBucket.getGcpBucket().getMetadata().getName(),
        sourceBucket.getResourceId(),
        sourceBucket.getGcpBucket().getMetadata().getWorkspaceId(),
        sourceProjectId,
        destinationBucketName,
        destinationWorkspaceId,
        destinationProjectId);
    CloneControlledGcpGcsBucketResult cloneResult = cloningUserResourceApi.cloneGcsBucket(
        cloneRequest,
        sourceBucket.getGcpBucket().getMetadata().getWorkspaceId(),
        sourceBucket.getResourceId());

    cloneResult = ClientTestUtils.pollWhileRunning(
        cloneResult,
        () -> cloningUserResourceApi.getCloneGcsBucketResult(
            cloneRequest.getDestinationWorkspaceId(),
            cloneRequest.getJobControl().getId()),
        CloneControlledGcpGcsBucketResult::getJobReport,
        Duration.ofSeconds(5));

    ClientTestUtils.assertJobSuccess("cloned bucket", cloneResult.getJobReport(), cloneResult.getErrorReport());

    final ClonedControlledGcpGcsBucket clonedBucket = cloneResult.getBucket();
    assertEquals(getWorkspaceId(), clonedBucket.getSourceWorkspaceId());
    assertEquals(sourceBucket.getResourceId(), clonedBucket.getSourceResourceId());

    final CreatedControlledGcpGcsBucket createdBucket = clonedBucket.getBucket();
    final GcpGcsBucketResource clonedResource = createdBucket.getGcpBucket();

    assertEquals(destinationBucketName, clonedResource.getAttributes().getBucketName());
    final ResourceMetadata clonedResourceMetadata = clonedResource.getMetadata();
    assertEquals(destinationWorkspaceId, clonedResourceMetadata.getWorkspaceId());
    assertEquals(sourceResourceName, clonedResourceMetadata.getName());
    assertEquals(clonedBucketDescription, clonedResourceMetadata.getDescription());
    final ResourceMetadata sourceMetadata = sourceBucket.getGcpBucket().getMetadata();
    assertEquals(CloningInstructionsEnum.NOTHING, clonedResourceMetadata.getCloningInstructions());
    assertEquals(
        sourceMetadata.getCloudPlatform(),
        clonedResourceMetadata.getCloudPlatform());
    assertEquals(
        ResourceType.GCS_BUCKET,
        clonedResourceMetadata.getResourceType());
    assertEquals(
        StewardshipType.CONTROLLED,
        clonedResourceMetadata.getStewardshipType());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getAccessScope(),
        clonedResourceMetadata.getControlledResourceMetadata().getAccessScope());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getManagedBy(),
        clonedResourceMetadata.getControlledResourceMetadata().getManagedBy());
    assertEquals(
        sourceMetadata.getControlledResourceMetadata().getPrivateResourceUser(),
        clonedResourceMetadata.getControlledResourceMetadata().getPrivateResourceUser());
    assertEquals(
        CloudPlatform.GCP,
        clonedResourceMetadata.getCloudPlatform());
    final Storage sourceProjectStorageClient = ClientTestUtils.getGcpStorageClient(testUser, sourceProjectId);
    final Bucket sourceGcsBucket = sourceProjectStorageClient.get(sourceBucketName);
    final Storage destinationProjectStorageClient = ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    final Bucket destinationGcsBucket = destinationProjectStorageClient.get(destinationBucketName);
    assertEquals(sourceGcsBucket.getStorageClass(), destinationGcsBucket.getStorageClass());
    assertEquals(sourceGcsBucket.getLocation(), destinationGcsBucket.getLocation()); // default since not specified
    assertEquals(LIFECYCLE_RULES.size(), destinationGcsBucket.getLifecycleRules().size());
    // We can't rely on the order of the lifecycle rules being maintained
    final LifecycleRule clonedDeleteRule = destinationGcsBucket.getLifecycleRules().stream()
        .filter(r -> DeleteLifecycleAction.TYPE.equals(r.getAction().getActionType()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find Delete lifecycle rule."));
    assertEquals(LIFECYCLE_RULE_1_CONDITION_AGE, clonedDeleteRule.getCondition().getAge());
    assertEquals(LIFECYCLE_RULE_1_CONDITION_LIVE, clonedDeleteRule.getCondition().getIsLive());
    assertEquals(StorageClass.ARCHIVE, clonedDeleteRule.getCondition().getMatchesStorageClass().get(0));
    assertEquals(LIFECYCLE_RULE_1_CONDITION_NUM_NEWER_VERSIONS, clonedDeleteRule.getCondition().getNumberOfNewerVersions());

    final LifecycleRule setStorageClassRule = destinationGcsBucket.getLifecycleRules().stream()
        .filter(r -> SetStorageClassLifecycleAction.TYPE.equals(r.getAction().getActionType()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find SetStorageClass lifecycle rule."));
    final SetStorageClassLifecycleAction setStorageClassLifecycleAction = (SetStorageClassLifecycleAction) setStorageClassRule.getAction();
    assertEquals(StorageClass.NEARLINE, setStorageClassLifecycleAction.getStorageClass());
    assertEquals(DateTime.parseRfc3339("2007-01-03"), setStorageClassRule.getCondition().getCreatedBefore());
    assertThat(setStorageClassRule.getCondition().getMatchesStorageClass(), contains(StorageClass.STANDARD));
    assertEquals(CloningInstructionsEnum.RESOURCE, clonedBucket.getEffectiveCloningInstructions());

    // test retrieving file from destination bucket
    Storage cloningUserStorageClient = ClientTestUtils.getGcpStorageClient(cloningUser, destinationProjectId);
    BlobId blobId = BlobId.of(destinationBucketName, GCS_BLOB_NAME);
    assertNotNull(blobId);

    final Blob retrievedFile = cloningUserStorageClient.get(blobId);
    assertNotNull(retrievedFile);
    assertEquals(blobId.getName(), retrievedFile.getBlobId().getName());
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) {
    if (destinationWorkspaceId != null) {
      try {
        final WorkspaceApi cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
        cloningUserWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
      } catch (ApiException | IOException e) {
        logger.error("Failed to clean up destination workspace: {}", e.getMessage());
      }
    }
  }
}
