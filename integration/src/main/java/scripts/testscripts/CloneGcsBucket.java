package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_LOCATION;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;
import static scripts.utils.GcsBucketTestFixtures.LIFECYCLE_RULES;
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
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.CreateControlledGcpGcsBucketRequestBody;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpGcsBucketCreationParameters;
import bio.terra.workspace.model.GcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.model.GcpGcsBucketLifecycle;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.ManagedBy;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneGcsBucket extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneGcsBucket.class);

  private TestUserSpecification user;
  private String sourceBucketName;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Create a source bucket
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // Create the source cloud context
    String sourceProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());
    ControlledGcpResourceApi resourceApi =
        ClientTestUtils.getControlledGcpResourceClient(testUser, server);

    // create source bucket
    final String nameSuffix = UUID.randomUUID().toString();
    final String sourceBucketName = BUCKET_PREFIX + nameSuffix;
    final String sourceResourceName = RESOURCE_PREFIX + nameSuffix; // TODO: why does this work? I thought hyphens were forbidden.
    final CreatedControlledGcpGcsBucket sourceBucket = createBucket(resourceApi, sourceBucketName, sourceResourceName);

    // create destination workspace
    final UUID destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    String projectId = CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, workspaceApi);
    logger.info("Created destination project {} in workspace {}", projectId, destinationWorkspaceId);
    final String destinationBucketName = "clone-" + nameSuffix;
    // clone the bucket
    final CloneControlledGcpGcsBucketRequest cloneRequest = new CloneControlledGcpGcsBucketRequest()
        .bucketName(destinationBucketName)
        .destinationWorkspaceId(destinationWorkspaceId)
        .name(sourceResourceName)
        .description("A cloned bucket")
        .location(null) // use same as src
        .cloningInstructions(CloningInstructionsEnum.DEFINITION);

    final CloneControlledGcpGcsBucketResult cloneResult = resourceApi.cloneGcsBucket(
        cloneRequest,
        sourceBucket.getGcpBucket().getMetadata().getWorkspaceId(),
        sourceBucket.getResourceId());
    final ClonedControlledGcpGcsBucket clonedBucket = cloneResult.getBucket();
    assertEquals(getWorkspaceId(), clonedBucket.getSourceWorkspaceId());
    assertEquals(sourceBucket.getResourceId(), clonedBucket.getSourceResourceId());
    final CreatedControlledGcpGcsBucket createdBucket = clonedBucket.getBucket();
    final GcpGcsBucketResource createdBucketResource = createdBucket.getGcpBucket();

    assertEquals(destinationBucketName, createdBucketResource.getAttributes().getBucketName());
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
