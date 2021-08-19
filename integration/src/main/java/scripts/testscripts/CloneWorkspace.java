package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static scripts.utils.ClientTestUtils.assertPresentAndGet;
import static scripts.utils.GcsBucketTestFixtures.RESOURCE_PREFIX;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserPrivate;
import static scripts.utils.ResourceMaker.makeControlledGcsBucketUserShared;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloneResourceResult;
import bio.terra.workspace.model.CloneWorkspaceRequest;
import bio.terra.workspace.model.CloneWorkspaceResult;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.ResourceCloneDetails;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataRepoTestScriptBase;

public class CloneWorkspace extends DataRepoTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(CloneWorkspace.class);
  private ControlledGcpResourceApi cloningUserResourceApi;
  private String sourceProjectId;
  private String nameSuffix;
  private String sharedSourceBucketName;
  private CreatedControlledGcpGcsBucket sharedSourceBucket;
  private String sharedBucketSourceResourceName;
  private TestUserSpecification cloningUser;
  private CreatedControlledGcpGcsBucket privateSourceBucket;
  // Roles to grant user on private resource
  private static final ImmutableList<ControlledResourceIamRole> PRIVATE_ROLES =
      ImmutableList.of(ControlledResourceIamRole.WRITER, ControlledResourceIamRole.EDITOR);

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, sourceOwnerWorkspaceApi);
    // set up 2 users
    assertThat(testUsers, hasSize(2));
    // user creating the source resource
    final TestUserSpecification sourceOwnerUser = testUsers.get(0);
    // user cloning the bucket resource
    cloningUser = testUsers.get(1);

    // Build source GCP project in main test workspace
    sourceProjectId = CloudContextMaker
        .createGcpCloudContext(getWorkspaceId(), sourceOwnerWorkspaceApi);
    logger.info("Created source project {} in workspace {}", sourceProjectId, getWorkspaceId());

    // add cloning user as reader on the workspace
    sourceOwnerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(cloningUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);

    // give users resource APIs
    final ControlledGcpResourceApi sourceOwnerResourceApi = ClientTestUtils
        .getControlledGcpResourceClient(sourceOwnerUser, server);
    cloningUserResourceApi = ClientTestUtils.getControlledGcpResourceClient(cloningUser, server);

    // Create a GCS bucket with data
    // create source bucket with COPY_RESOURCE - should clone fine
    nameSuffix = UUID.randomUUID().toString();
    sharedBucketSourceResourceName = RESOURCE_PREFIX + nameSuffix;
    sharedSourceBucket = makeControlledGcsBucketUserShared(sourceOwnerResourceApi, getWorkspaceId(),
        sharedBucketSourceResourceName, CloningInstructionsEnum.RESOURCE);
    sharedSourceBucketName = sharedSourceBucket.getGcpBucket().getAttributes().getBucketName();

    // create a private GCS bucket, which the non-creating user can't clone
    final PrivateResourceIamRoles privateRoles = new PrivateResourceIamRoles();
    privateRoles.addAll(PRIVATE_ROLES);
    privateSourceBucket = makeControlledGcsBucketUserPrivate(sourceOwnerResourceApi, getWorkspaceId(),
        UUID.randomUUID().toString(), sourceOwnerUser.userEmail, privateRoles, CloningInstructionsEnum.RESOURCE);

    // create a GCS bucket with data and COPY_NOTHING instruction
    // create a GCS bucket with data and COPY_DEFINITION
    // Create a BigQuery Dataset with tables and COPY_RESOURCE
    // Create a BigQuery dataset with tables and COPY_DEFINITION
    // Create a private BQ dataset
    // Create reference to GCS bucket with COPY_REFERENCE
    // create reference to BQ dataset with COPY_NOTHING
    // create reference to Data Repo Snapshot
    // Give the second user read access to the workspace
  }

  @Override
  protected void doUserJourney(TestUserSpecification sourceOwnerUser, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {
    // As reader user, clone the workspace
    // Get a new workspace API for the reader
    final WorkspaceApi cloningUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(cloningUser, server);
    final CloneWorkspaceRequest cloneWorkspaceRequest = new CloneWorkspaceRequest()
        .location("us-central1");
    CloneWorkspaceResult cloneResult = cloningUserWorkspaceApi.cloneWorkspace(cloneWorkspaceRequest, getWorkspaceId());

    final String jobId = cloneResult.getJobReport().getId();
    cloneResult = ClientTestUtils.pollWhileRunning(
        cloneResult,
        () -> cloningUserWorkspaceApi.getCloneWorkspaceResult(getWorkspaceId(), jobId),
        CloneWorkspaceResult::getJobReport,
        Duration.ofSeconds(10));
    logger.info("Clone result: {}", cloneResult);
    ClientTestUtils.assertJobSuccess("Clone Workspace", cloneResult.getJobReport(), cloneResult.getErrorReport());
    assertNull(cloneResult.getErrorReport());

    assertThat(cloneResult.getWorkspace().getResources(), hasSize(2));
    assertEquals(getWorkspaceId(), cloneResult.getWorkspace().getSourceWorkspaceId());
    final UUID destinationWorkspaceId = cloneResult.getWorkspace().getDestinationWorkspaceId();
    assertNotNull(destinationWorkspaceId);

    // Verify shared GCS bucket details
    final ResourceCloneDetails sharedBucketCloneDetails = assertPresentAndGet(
        cloneResult.getWorkspace().getResources().stream()
        .filter(r -> sharedSourceBucket.getResourceId().equals(r.getSourceResourceId()))
        .findFirst());
    assertEquals(CloningInstructionsEnum.RESOURCE, sharedBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, sharedBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, sharedBucketCloneDetails.getStewardshipType());
    assertNotNull(sharedBucketCloneDetails.getDestinationResourceId());
    assertEquals(CloneResourceResult.SUCCEEDED, sharedBucketCloneDetails.getResult());
    // assume data transfer is covered in other tests

    // Verify clone of private bucket fails
    final ResourceCloneDetails privateBucketCloneDetails = assertPresentAndGet(
        cloneResult.getWorkspace().getResources().stream()
            .filter(r -> privateSourceBucket.getResourceId().equals(r.getSourceResourceId()))
            .findFirst());
    assertEquals(CloningInstructionsEnum.RESOURCE, privateBucketCloneDetails.getCloningInstructions());
    assertEquals(ResourceType.GCS_BUCKET, privateBucketCloneDetails.getResourceType());
    assertEquals(StewardshipType.CONTROLLED, privateBucketCloneDetails.getStewardshipType());
    assertNull(privateBucketCloneDetails.getDestinationResourceId());
    assertEquals(CloneResourceResult.FAILED, privateBucketCloneDetails.getResult());

    // Verify COPY_NOTHING bucket was skipped
    // verify COPY_DEFINITION bucket exists but is empty
    // verify COPY_RESOURCE bucket exists and has data
    // verify COPY_DEFINITION dataset exists but has no tables
    // verify private dataset clone failed

  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Delete the cloned workspace (will delete contents)
  }
}
