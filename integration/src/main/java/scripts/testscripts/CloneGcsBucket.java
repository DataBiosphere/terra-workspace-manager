package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_LOCATION;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULE_1;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_DESCRIPTION;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketRequest;
import bio.terra.workspace.model.CloneControlledGcpGcsBucketResult;
import bio.terra.workspace.model.ClonedControlledGcpGcsBucket;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketLifecycleRule;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.JobReport.StatusEnum;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneGcsBucket extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneGcsBucket.class);

  private ControlledGcpResourceApi resourceApi;
  private CreatedControlledGcpGcsBucket sourceBucket;
  private String destinationProjectId;
  private String nameSuffix;
  private String sourceBucketName;
  private String sourceProjectId;
  private String sourceResourceName;
  private UUID destinationWorkspaceId;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Create the source cloud context
    sourceProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // Create a source bucket
    // Create the source cloud context
    resourceApi = ClientTestUtils.getControlledGcpResourceClient(testUsers.get(0), server);

    // create source bucket
    nameSuffix = UUID.randomUUID().toString();
    sourceBucketName = BUCKET_PREFIX + nameSuffix;
    sourceResourceName = RESOURCE_PREFIX + nameSuffix;
    sourceBucket = createBucket(resourceApi, sourceBucketName, sourceResourceName);

    // create destination workspace
    destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    destinationProjectId = CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, workspaceApi);
    logger.info("Created destination project {} in workspace {}", destinationProjectId, destinationWorkspaceId);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
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
        .cloningInstructions(CloningInstructionsEnum.DEFINITION)
        .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    logger.info("Cloning bucket name: {} resource ID: {} workspace: {} into bucket name: {} workspace: {}",
        sourceBucket.getGcpBucket().getMetadata().getName(),
        sourceBucket.getResourceId(),
        sourceBucket.getGcpBucket().getMetadata().getWorkspaceId(),
        destinationBucketName,
        destinationWorkspaceId);
    CloneControlledGcpGcsBucketResult cloneResult = resourceApi.cloneGcsBucket(
        cloneRequest,
        sourceBucket.getGcpBucket().getMetadata().getWorkspaceId(),
        sourceBucket.getResourceId());
//    logger.info("Clone result: {}", cloneResult);
    StatusEnum status = cloneResult.getJobReport().getStatus();
    while (status.equals(StatusEnum.RUNNING)) {
      TimeUnit.SECONDS.sleep(5);
      // get new status
      cloneResult = resourceApi.getCloneGcsBucketResult(
          cloneRequest.getDestinationWorkspaceId(),
          cloneRequest.getJobControl().getId());
      status = cloneResult.getJobReport().getStatus();
      logger.info("Clone status is {}", status);
    }
    assertEquals(StatusEnum.SUCCEEDED, status);
    logger.info("Successfully cloned bucket with result {}", cloneResult);

    final ClonedControlledGcpGcsBucket clonedBucket = cloneResult.getBucket();
//    logger.info("Cloned bucket: {}", clonedBucket);
    assertEquals(getWorkspaceId(), clonedBucket.getSourceWorkspaceId());
    assertEquals(sourceBucket.getResourceId(), clonedBucket.getSourceResourceId());
    final CreatedControlledGcpGcsBucket createdBucket = clonedBucket.getBucket();
//    logger.info("Created bucket: {}", createdBucket);
    final GcpGcsBucketResource clonedResource = createdBucket.getGcpBucket();
//    logger.info("Created bucket resource: {}", clonedResource);
    assertEquals(destinationBucketName, clonedResource.getAttributes().getBucketName());
    ResourceMetadata clonedResourceMetadata = clonedResource.getMetadata();
    assertEquals(destinationWorkspaceId, clonedResourceMetadata.getWorkspaceId());
    assertEquals(sourceResourceName, clonedResourceMetadata.getName());
    assertEquals(clonedBucketDescription, clonedResourceMetadata.getDescription());
    final ResourceMetadata sourceMetadata = sourceBucket.getGcpBucket().getMetadata();
    assertEquals(CloningInstructionsEnum.DEFINITION, clonedResourceMetadata.getCloningInstructions());
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
    final Storage destinationProjectStorageClient = ClientTestUtils.getGcpStorageClient(testUser, destinationProjectId);
    final Bucket destinationGcsBucket = destinationProjectStorageClient.get(destinationBucketName);
    assertEquals(sourceGcsBucket.getStorageClass(), destinationGcsBucket.getStorageClass());
    assertEquals(sourceGcsBucket.getLocation(), destinationGcsBucket.getLocation()); // default since not specified
    assertEquals(LIFECYCLE_RULES.size(), destinationGcsBucket.getLifecycleRules().size());
    // We can't rely on the order of the lifecycle rules being maintained
    final LifecycleRule clonedDeleteRule = destinationGcsBucket.getLifecycleRules().stream()
        .filter(r -> DeleteLifecycleAction.TYPE.equals(r.getAction().getActionType()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find Delete lifecycle rule."));
    assertEquals(64, clonedDeleteRule.getCondition().getAge());
    assertTrue(clonedDeleteRule.getCondition().getIsLive());
    assertThat(clonedDeleteRule.getCondition().getMatchesStorageClass(), contains(StorageClass.ARCHIVE));
    assertEquals(2, clonedDeleteRule.getCondition().getNumberOfNewerVersions());

    final LifecycleRule setStorageClassRule = destinationGcsBucket.getLifecycleRules().stream()
        .filter(r -> SetStorageClassLifecycleAction.TYPE.equals(r.getAction().getActionType()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Can't find SetStorageClass lifecycle rule."));
    final SetStorageClassLifecycleAction setStorageClassLifecycleAction = (SetStorageClassLifecycleAction) setStorageClassRule.getAction();
    assertEquals(StorageClass.NEARLINE, setStorageClassLifecycleAction.getStorageClass());
    assertEquals(DateTime.parseRfc3339("2007-01-03"), setStorageClassRule.getCondition().getCreatedBefore());
    assertThat(setStorageClassRule.getCondition().getMatchesStorageClass(), contains(StorageClass.STANDARD));
  }

  private CreatedControlledGcpGcsBucket createBucket(ControlledGcpResourceApi resourceApi, String bucketName, String resourceName)
    throws ApiException {
    var creationParameters =
        new GcpGcsBucketCreationParameters()
            .name(bucketName)
            .location(BUCKET_LOCATION)
            .defaultStorageClass(GcpGcsBucketDefaultStorageClass.STANDARD)
            .lifecycle(new GcpGcsBucketLifecycle().rules(LIFECYCLE_RULES));

    var commonParameters =
        new ControlledResourceCommonFields()
            .name(resourceName)
            .description(RESOURCE_DESCRIPTION)
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.SHARED_ACCESS)
            .managedBy(ManagedBy.USER);

    var body =
        new CreateControlledGcpGcsBucketRequestBody()
            .gcsBucket(creationParameters)
            .common(commonParameters);
    logger.info("Attempting to create bucket {} workspace {}", bucketName, getWorkspaceId());
    return resourceApi.createBucket(body, getWorkspaceId());
  }
}
