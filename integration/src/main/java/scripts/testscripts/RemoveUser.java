package scripts.testscripts;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.GcsBucketUtils.BUCKET_PREFIX;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserPrivate;
import static scripts.utils.GcsBucketUtils.makeControlledGcsBucketUserShared;

import bio.terra.common.sam.SamRetry;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpBigQueryDatasetResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.broadinstitute.dsde.workbench.client.sam.api.GroupApi;
import scripts.utils.BqDataTableUtils;
import scripts.utils.BqDatasetUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.GcsBucketObjectUtils;
import scripts.utils.GcsBucketUtils;
import scripts.utils.NotebookUtils;
import scripts.utils.RetryUtils;
import scripts.utils.SamClientUtils;
import scripts.utils.TestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class RemoveUser extends WorkspaceAllocateTestScriptBase {

  private TestUserSpecification privateResourceUser;
  private TestUserSpecification sharedResourceUser;
  private String projectId;
  private String groupEmail;
  private String groupName;
  private CreatedControlledGcpGcsBucket sharedBucket;
  private CreatedControlledGcpGcsBucket privateBucket;
  private GcpBigQueryDatasetResource privateDataset;
  private CreatedControlledGcpAiNotebookInstanceResult privateNotebook;
  private GroupApi samGroupApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi ownerWorkspaceApi)
      throws Exception {
    super.doSetup(testUsers, ownerWorkspaceApi);
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    TestUserSpecification workspaceOwner = testUsers.get(0);
    this.privateResourceUser = testUsers.get(1);
    this.sharedResourceUser = testUsers.get(2);
    assertNotEquals(
        privateResourceUser.userEmail,
        sharedResourceUser.userEmail,
        "The two test users are distinct");

    // Create a group
    groupName = TestUtils.appendRandomNumber("groupremovetest");
    samGroupApi = SamClientUtils.samGroupApi(testUsers.get(1), server);
    SamRetry.retry(() -> samGroupApi.postGroup(groupName, null));
    groupEmail = samGroupApi.getGroup(groupName);

    // Add one group as a reader.
    ownerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(groupEmail), getWorkspaceId(), IamRole.READER);

    // Add one user as a reader, and one as both a reader and writer.
    ClientTestUtils.grantRole(
        ownerWorkspaceApi, getWorkspaceId(), privateResourceUser, IamRole.READER);
    ClientTestUtils.grantRole(
        ownerWorkspaceApi, getWorkspaceId(), privateResourceUser, IamRole.WRITER);
    ClientTestUtils.grantRole(
        ownerWorkspaceApi, getWorkspaceId(), sharedResourceUser, IamRole.WRITER);

    // Create a GCP cloud context.
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), ownerWorkspaceApi);
    ClientTestUtils.workspaceRoleWaitForPropagation(privateResourceUser, projectId);
    ClientTestUtils.workspaceRoleWaitForPropagation(sharedResourceUser, projectId);

    // Create a shared GCS bucket with one object inside.
    ControlledGcpResourceApi ownerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(workspaceOwner, server);
    String sharedBucketName = BUCKET_PREFIX + UUID.randomUUID();
    sharedBucket =
        makeControlledGcsBucketUserShared(
            ownerResourceApi, getWorkspaceId(), sharedBucketName, CloningInstructionsEnum.NOTHING);
    GcsBucketUtils.addFileToBucket(sharedBucket, workspaceOwner, projectId);

    // Create a private GCS bucket for privateResourceUser with one object inside.
    String privateBucketName = BUCKET_PREFIX + UUID.randomUUID();
    ControlledGcpResourceApi privateUserResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceUser, server);
    privateBucket =
        makeControlledGcsBucketUserPrivate(
            privateUserResourceApi,
            getWorkspaceId(),
            privateBucketName,
            CloningInstructionsEnum.NOTHING);
    GcsBucketUtils.addFileToBucket(privateBucket, privateResourceUser, projectId);

    // Create a private BQ dataset for privateResourceUser and populate it.
    String datasetResourceName = RandomStringUtils.randomAlphabetic(8).toLowerCase();
    privateDataset =
        BqDatasetUtils.makeControlledBigQueryDatasetUserPrivate(
            privateUserResourceApi,
            getWorkspaceId(),
            datasetResourceName,
            null,
            CloningInstructionsEnum.NOTHING);
    BqDatasetUtils.populateBigQueryDataset(privateDataset, privateResourceUser, projectId);
    // Create a private notebook for privateResourceUser.
    String notebookInstanceId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
    privateNotebook =
        NotebookUtils.makeControlledNotebookUserPrivate(
            getWorkspaceId(),
            notebookInstanceId,
            /* location= */ null,
            privateUserResourceApi,
            /* testValue= */ null,
            /* postStartupScript= */ null);
  }

  @Override
  protected void doUserJourney(TestUserSpecification workspaceOwner, WorkspaceApi ownerWorkspaceApi)
      throws Exception {
    String sharedBucketName = sharedBucket.getGcpBucket().getAttributes().getBucketName();
    String privateBucketName = privateBucket.getGcpBucket().getAttributes().getBucketName();
    // Validate that setup ran correctly and users have appropriate resource access.
    RetryUtils.getWithRetryOnException(
        () ->
            GcsBucketObjectUtils.retrieveBucketFile(
                sharedBucketName, projectId, sharedResourceUser));
    RetryUtils.getWithRetryOnException(
        () ->
            GcsBucketObjectUtils.retrieveBucketFile(
                privateBucketName, projectId, privateResourceUser));

    // Remove group from READER role
    try {
      ownerWorkspaceApi.removeRole(getWorkspaceId(), IamRole.READER, groupEmail);
    } finally {
      SamRetry.retry(() -> samGroupApi.deleteGroup(groupName));
    }

    // Remove WRITER role from sharedResourceUser. This is their only role, so they are no longer
    // a member of this workspace.
    ownerWorkspaceApi.removeRole(getWorkspaceId(), IamRole.WRITER, sharedResourceUser.userEmail);

    // Validate that sharedResourceUser can no longer read resources in the workspace.
    // This requires syncing google groups, so there is often a delay that we need to wait for.
    RetryUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(sharedBucketName, sharedResourceUser));

    // privateResource user can still read the shared bucket.
    GcsBucketObjectUtils.retrieveBucketFile(sharedBucketName, projectId, privateResourceUser);

    // Remove WRITER role from privateResourceUser. They are also a reader, so they should not lose
    // access to workspace resources because of this. Note: we are assuming propagation to the
    // bucket
    // is the same as propagation to the project.
    boolean revokeWriterSucceeded =
        ClientTestUtils.revokeRoleWaitForPropagation(
            ownerWorkspaceApi, getWorkspaceId(), projectId, privateResourceUser, IamRole.WRITER);
    assertTrue(revokeWriterSucceeded);

    // Validate privateResourceUser still has access to all resources.
    GcsBucketObjectUtils.retrieveBucketFile(sharedBucketName, projectId, privateResourceUser);
    GcsBucketObjectUtils.retrieveBucketFile(privateBucketName, projectId, privateResourceUser);
    BqDataTableUtils.readPopulatedBigQueryTable(privateDataset, privateResourceUser, projectId);
    assertTrue(NotebookUtils.userHasProxyAccess(privateNotebook, privateResourceUser, projectId));

    // Remove READER role from privateResourceUser. This is their last role, so they are no longer
    // a member of this workspace.
    boolean revokeReaderSucceeded =
        ClientTestUtils.revokeRoleWaitForPropagation(
            ownerWorkspaceApi, getWorkspaceId(), projectId, privateResourceUser, IamRole.READER);
    assertTrue(revokeReaderSucceeded);

    // Validate privateResourceWriter no longer has access to any private resources.
    RetryUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(sharedBucketName, privateResourceUser));
    RetryUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(privateBucketName, privateResourceUser));
    RetryUtils.runWithRetryOnException(
        () -> assertUserCannotReadDataset(privateDataset, privateResourceUser));
    RetryUtils.runWithRetryOnException(
        () -> assertUserCannotAccessNotebook(privateNotebook, privateResourceUser));
  }

  /**
   * An assertion that the given user cannot read from the given bucket. This is pulled into a
   * separate function to make retrying simpler.
   */
  private void assertUserCannotReadBucket(String bucketName, TestUserSpecification testUser) {
    try {
      GcsBucketObjectUtils.retrieveBucketFile(bucketName, projectId, testUser);
      // If nothing is thrown, that's bad! The user actually can read the bucket.
      throw new RuntimeException(
          String.format(
              "User %s is still able to access bucket %s", testUser.userEmail, bucketName));
    } catch (StorageException googleError) {
      // If this is a 403 error, the user was successfully removed from the bucket.
      assertEquals(SC_FORBIDDEN, googleError.getCode());
    } catch (Exception e) {
      // Unexpected error, rethrow
      throw new RuntimeException("Error checking user is removed from bucket", e);
    }
  }

  /**
   * An assertion that the given user cannot read from the given dataset. This is pulled into a
   * separate function to make retrying simpler.
   */
  private void assertUserCannotReadDataset(
      GcpBigQueryDatasetResource dataset, TestUserSpecification testUser) {
    try {
      BqDataTableUtils.readPopulatedBigQueryTable(dataset, testUser, projectId);
      // If nothing is thrown, that's bad! The user actually can read the dataset.
      throw new RuntimeException(
          String.format(
              "User %s is still able to access dataset %s",
              testUser.userEmail, dataset.getMetadata().getResourceId()));
    } catch (BigQueryException googleError) {
      // If this is a 403 error, the user was successfully removed from the dataset.
      assertEquals(SC_FORBIDDEN, googleError.getCode());
    } catch (IOException | InterruptedException e) {
      // Unexpected error, rethrow
      throw new RuntimeException("Error checking user is removed from dataset", e);
    }
  }

  /**
   * An assertion that the given user cannot access the given notebook. This is pulled into a
   * separate function to make retrying simpler.
   */
  private void assertUserCannotAccessNotebook(
      CreatedControlledGcpAiNotebookInstanceResult createdNotebook,
      TestUserSpecification testUser) {
    try {
      if (NotebookUtils.userHasProxyAccess(createdNotebook, testUser, projectId)) {
        throw new RuntimeException(
            String.format(
                "User %s is still able to access notebook %s",
                testUser.userEmail,
                createdNotebook.getAiNotebookInstance().getMetadata().getResourceId()));
      }
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Error checking notebook access", e);
    }
  }
}
