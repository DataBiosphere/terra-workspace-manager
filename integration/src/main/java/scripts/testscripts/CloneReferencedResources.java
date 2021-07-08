package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static scripts.utils.ClientTestUtils.TEST_BUCKET_NAME;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.CloneReferencedResourceRequestBody;
import bio.terra.workspace.model.CloneReferencedResourceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.ResourceType;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class CloneReferencedResources extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(CloneReferencedResources.class);
  private static final String CLONED_BUCKET_NAME = "a_new_name";
  private GcpGcsBucketResource sourceBucketReference;

  private UUID destinationWorkspaceId;
  private String destinationProjectId;
  private ReferencedGcpResourceApi referencedGcpResourceApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    ApiClient apiClient = ClientTestUtils.getClientForTestUser(testUsers.get(0), server);
    referencedGcpResourceApi = new ReferencedGcpResourceApi(apiClient);
    String bucketReferenceName = RandomStringUtils.random(16, true, false);
    // create reference to existing test bucket
    sourceBucketReference =
        ResourceMaker
            .makeGcsBucketReference(referencedGcpResourceApi, getWorkspaceId(), bucketReferenceName);

    // create a new workspace with cloud context
    destinationWorkspaceId = UUID.randomUUID();
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(destinationWorkspaceId)
            .spendProfile(getSpendProfileId())
            .stage(getStageModel());
    final CreatedWorkspace createdDestinationWorkspace = workspaceApi.createWorkspace(requestBody);
    assertThat(createdDestinationWorkspace.getId(), equalTo(destinationWorkspaceId));

    // create destination cloud context
    destinationProjectId = CloudContextMaker
        .createGcpCloudContext(destinationWorkspaceId, workspaceApi);
    logger.info("Created destination project {} in workspace {}", destinationProjectId, destinationWorkspaceId);

  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // clone source reference to destination
    final var body = new CloneReferencedResourceRequestBody()
        .cloningInstructions(CloningInstructionsEnum.REFERENCE)
        .name(CLONED_BUCKET_NAME)
        .destinationWorkspaceId(destinationWorkspaceId);
    final CloneReferencedResourceResult cloneResult = referencedGcpResourceApi.cloneReferencedResource(body,
        getWorkspaceId(),
        sourceBucketReference.getMetadata().getResourceId());
    assertEquals(getWorkspaceId(), cloneResult.getSourceWorkspaceId());
    assertEquals(CLONED_BUCKET_NAME, cloneResult.getResource().getMetadata().getName());
    assertEquals(ResourceType.GCS_BUCKET, cloneResult.getResource().getMetadata().getResourceType());
    assertEquals(TEST_BUCKET_NAME,
        cloneResult.getResource().getResourceAttributes().getGcpGcsBucket().getBucketName());

  }
}
