package scripts.testscripts;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.GcsBucketTestFixtures.BUCKET_PREFIX;

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
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.ResourceModifier;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class RemoveUser extends WorkspaceAllocateTestScriptBase {

  private TestUserSpecification privateResourceUser;
  private TestUserSpecification sharedResourceUser;
  private String projectId;
  private CreatedControlledGcpGcsBucket sharedBucket;
  private CreatedControlledGcpGcsBucket privateBucket;
  private GcpBigQueryDatasetResource privateDataset;
  private CreatedControlledGcpAiNotebookInstanceResult privateNotebook;

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

    // Add one user as a reader, and one as both a reader and writer.
    ownerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(privateResourceUser.userEmail),
        getWorkspaceId(),
        IamRole.READER);
    ownerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(privateResourceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);
    ownerWorkspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(sharedResourceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);

    // Create a GCP cloud context.
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), ownerWorkspaceApi);

    // Create a shared GCS bucket with one object inside.
    ControlledGcpResourceApi ownerResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(workspaceOwner, server);
    String sharedBucketName = BUCKET_PREFIX + UUID.randomUUID();
    sharedBucket =
        ResourceMaker.makeControlledGcsBucketUserShared(
            ownerResourceApi, getWorkspaceId(), sharedBucketName, CloningInstructionsEnum.NOTHING);
    ResourceModifier.addFileToBucket(sharedBucket, workspaceOwner, projectId);

    // Create a private GCS bucket for privateResourceUser with one object inside.
    String privateBucketName = BUCKET_PREFIX + UUID.randomUUID();
    ControlledGcpResourceApi privateUserResourceApi =
        ClientTestUtils.getControlledGcpResourceClient(privateResourceUser, server);
    privateBucket =
        ResourceMaker.makeControlledGcsBucketUserPrivate(
            privateUserResourceApi,
            getWorkspaceId(),
            privateBucketName,
            CloningInstructionsEnum.NOTHING);
    ResourceModifier.addFileToBucket(privateBucket, privateResourceUser, projectId);

    // Create a private BQ dataset for privateResourceUser and populate it.
    String datasetResourceName = RandomStringUtils.randomAlphabetic(8).toLowerCase();
    privateDataset =
        ResourceMaker.makeControlledBigQueryDatasetUserPrivate(
            privateUserResourceApi,
            getWorkspaceId(),
            datasetResourceName,
            null,
            CloningInstructionsEnum.NOTHING);
    ResourceModifier.populateBigQueryDataset(privateDataset, privateResourceUser, projectId);
    // Create a private notebook for privateResourceUser.
    String notebookInstanceId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
    privateNotebook =
        ResourceMaker.makeControlledNotebookUserPrivate(
            getWorkspaceId(), notebookInstanceId, /*location=*/null, privateUserResourceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification workspaceOwner, WorkspaceApi ownerWorkspaceApi)
      throws Exception {
    String sharedBucketName = sharedBucket.getGcpBucket().getAttributes().getBucketName();
    String privateBucketName = privateBucket.getGcpBucket().getAttributes().getBucketName();
    // Validate that setup ran correctly and users have appropriate resource access.
    ResourceModifier.retrieveBucketFile(sharedBucketName, projectId, sharedResourceUser);
    ResourceModifier.retrieveBucketFile(privateBucketName, projectId, privateResourceUser);

    // Remove WRITER role from sharedResourceUser. This is their only role, so they are no longer
    // a member of this workspace.
    ownerWorkspaceApi.removeRole(getWorkspaceId(), IamRole.WRITER, sharedResourceUser.userEmail);

    // Validate that sharedResourceUser can no longer read resources in the workspace.
    // This requires syncing google groups, so there is often a delay that we need to wait for.
    ClientTestUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(sharedBucketName, sharedResourceUser));

    // privateResource user can still read the shared bucket.
    ResourceModifier.retrieveBucketFile(sharedBucketName, projectId, privateResourceUser);

    // Remove READER role from privateResourceUser. They are also a writer, so they should not lose
    // access to workspace resources because of this.
    ownerWorkspaceApi.removeRole(getWorkspaceId(), IamRole.READER, privateResourceUser.userEmail);

    // Validate privateResourceWriter still has access to all resources.
    ResourceModifier.retrieveBucketFile(sharedBucketName, projectId, privateResourceUser);
    ResourceModifier.retrieveBucketFile(privateBucketName, projectId, privateResourceUser);
    ResourceModifier.readPopulatedBigQueryTable(privateDataset, privateResourceUser, projectId);
    assertTrue(
        ResourceModifier.userHasProxyAccess(privateNotebook, privateResourceUser, projectId));

    // Remove WRITER role from privateResourceUser. This is their last role, so they are no longer
    // a member of this workspace.
    ownerWorkspaceApi.removeRole(getWorkspaceId(), IamRole.WRITER, privateResourceUser.userEmail);

    // Validate privateResourceWriter no longer has access to any private resources.
    ClientTestUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(sharedBucketName, privateResourceUser));
    ClientTestUtils.runWithRetryOnException(
        () -> assertUserCannotReadBucket(privateBucketName, privateResourceUser));
    ClientTestUtils.runWithRetryOnException(
        () -> assertUserCannotReadDataset(privateDataset, privateResourceUser));
    ClientTestUtils.runWithRetryOnException(
        () -> assertUserCannotAccessNotebook(privateNotebook, privateResourceUser));
  }

  /**
   * An assertion that the given user cannot read from the given bucket. This is pulled into a
   * separate function to make retrying simpler.
   */
  private void assertUserCannotReadBucket(String bucketName, TestUserSpecification testUser) {
    try {
      ResourceModifier.retrieveBucketFile(bucketName, projectId, testUser);
      // If nothing is thrown, that's bad! The user actually can read the bucket.
      throw new RuntimeException(
          String.format(
              "User %s is still able to access bucket %s", testUser.userEmail, bucketName));
    } catch (StorageException googleError) {
      // If this is a 403 error, the user was successfully removed from the bucket.
      assertEquals(SC_FORBIDDEN, googleError.getCode());
    } catch (IOException e) {
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
      ResourceModifier.readPopulatedBigQueryTable(dataset, testUser, projectId);
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
      if (ResourceModifier.userHasProxyAccess(createdNotebook, testUser, projectId)) {
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
