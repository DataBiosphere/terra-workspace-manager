package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static scripts.utils.GcsBucketUtils.GCS_BLOB_CONTENT;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.model.ControlledResourceIamRole;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for testing access to a GCS bucket
 *
 * <p>The constructor requires a test user that is able to create a "reference" blob in the bucket.
 * The methods test the access levels using the ControlledResourceIamRole names and reports success
 * or failure. One instance of the class can be used for multiple role tests.
 *
 * <p>We test 6 operations:
 *
 * <ul>
 *   <li>read an existing blob
 *   <li>create a new blob
 *   <li>update a blob
 *   <li>delete a blob
 *   <li>get bucket metadata
 *   <li>delete the bucket (no role should be able to do this one)
 * </ul>
 *
 * Only listing the abilities:
 *
 * <ul>
 *   <li>READER can read a blob
 *   <li>WRITER can create, read, update, delete
 *   <li>EDITOR can create, read, update, delete, and get metadata
 *   <li>null indicates no role and has no abilities
 * </ul>
 *
 * <p>When an access change is made, we need to wait for the change to propagate, but since we also
 * expect many failures, we do not want to wait every time. If requested, using checkAccessWait, we
 * will wait on the first operation. Otherwise, we do not wait on any operations.
 */
public class GcsBucketAccessTester implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(GcsBucketAccessTester.class);
  private final TestUserSpecification creatorTestUser;
  private final String bucketName;
  private final String projectId;
  private BlobId testBlobId;
  private final List<BlobId> createdBlobs;
  private boolean needToWait;
  private Storage testClient;

  public GcsBucketAccessTester(
      TestUserSpecification creatorTestUser, String bucketName, String projectId) throws Exception {
    this.creatorTestUser = creatorTestUser;
    this.bucketName = bucketName;
    this.projectId = projectId;
    this.testBlobId = null;
    this.needToWait = false;
    this.createdBlobs = new ArrayList<>();
  }

  @Override
  public void close() throws Exception {
    // Clean up any created blob so that delete happens quickly, lest the test timeout waiting.
    Storage creatorClient = ClientTestUtils.getGcpStorageClient(creatorTestUser, projectId);
    // Blob deletion permission can be different for individual blobs, so use retries here.
    needToWait = true;
    for (BlobId blobId : createdBlobs) {
      boolean found = doWithOptionalWait(() -> creatorClient.delete(blobId));
      logger.info("Blob {} was {}", blobId.getName(), (found ? "found and deleted" : "not found"));
    }
  }

  public void checkAccessWait(
      TestUserSpecification testUser, @Nullable ControlledResourceIamRole role) throws Exception {
    needToWait = true;
    checkAccess(testUser, role);
  }

  /**
   * @param testUser user to check
   * @param role the IamRole to test for; null to test the "user has no role" case
   * @throws Exception from asserts if the user has unexpected access
   */
  public void checkAccess(TestUserSpecification testUser, @Nullable ControlledResourceIamRole role)
      throws Exception {
    logger.info(
        "Checking access of {} for role {} with wait {}",
        testUser.userEmail,
        (role == null ? "none" : role.toString()),
        needToWait);

    testClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    if (role == null) {
      // Do not wait for permissions which won't appear
      needToWait = false;
      assertFalse(doWithOptionalWait(() -> blobCreate(testClient)), "no role cannot create");
      assertFalse(doWithOptionalWait(this::blobRead), "no role cannot read");
      assertFalse(doWithOptionalWait(this::blobUpdate), "no role cannot update");
      assertFalse(doWithOptionalWait(this::blobDelete), "no role cannot delete");
      assertFalse(doWithOptionalWait(this::bucketGet), "no role cannot bucket get");
      assertFalse(doWithOptionalWait(this::bucketDelete), "no role cannot bucket delete");
      return;
    }

    switch (role) {
      case READER:
        assertTrue(doWithOptionalWait(this::blobRead), "reader can read");
        // Do not wait for permissions which won't appear
        needToWait = false;
        assertFalse(doWithOptionalWait(() -> blobCreate(testClient)), "reader cannot create");
        assertFalse(doWithOptionalWait(this::blobUpdate), "reader cannot update");
        assertFalse(doWithOptionalWait(this::blobDelete), "reader cannot delete");
        assertFalse(doWithOptionalWait(this::bucketGet), "reader cannot bucket get");
        assertFalse(doWithOptionalWait(this::bucketDelete), "reader cannot bucket delete");
        return;

      case WRITER:
        assertTrue(doWithOptionalWait(() -> blobCreate(testClient)), "writer can create");
        assertTrue(doWithOptionalWait(this::blobRead), "writer can read");
        assertTrue(doWithOptionalWait(this::blobUpdate), "writer can update");
        assertTrue(doWithOptionalWait(this::blobDelete), "writer can delete");
        // Do not wait for permissions which won't appear
        needToWait = false;
        assertFalse(doWithOptionalWait(this::bucketGet), "writer cannot bucket get");
        assertFalse(doWithOptionalWait(this::bucketDelete), "writer cannot bucket delete");
        return;

      case EDITOR:
        assertTrue(doWithOptionalWait(() -> blobCreate(testClient)), "editor can create");
        assertTrue(doWithOptionalWait(this::blobRead), "editor can read");
        assertTrue(doWithOptionalWait(this::blobUpdate), "editor can update");
        assertTrue(doWithOptionalWait(this::blobDelete), "editor can delete");
        assertTrue(doWithOptionalWait(this::bucketGet), "editor can bucket get");
        // Do not wait for permissions which won't appear
        needToWait = false;
        assertFalse(doWithOptionalWait(this::bucketDelete), "editor cannot bucket delete");
        return;

      default:
        throw new IllegalArgumentException("Invalid IAM role: " + role);
    }
  }

  // Common logic for optionally waiting on the first check.

  @FunctionalInterface
  public interface TestFunction {
    Object apply() throws Exception;
  }

  private boolean doWithOptionalWait(TestFunction function) throws Exception {
    try {
      if (needToWait) {
        ClientTestUtils.getWithRetryOnException(function::apply);
      } else {
        function.apply();
      }
      return true;
    } catch (StorageException e) {
      assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, e.getCode());
      return false;
    }
  }

  // Simple operations that wrap the storage calls so they are homogeneous and
  // can be used as lambdas. These return Object so I can reuse getWithRetryOnException.

  // blobCreate doesn't follow the pattern of the other actions, because it is used
  // by both the creatorTestUser and the checkAccess testUser.
  private BlobId blobCreate(Storage storageClient) throws StorageException {
    BlobId blobId = BlobId.of(bucketName, makeBlobName());
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
    storageClient.create(blobInfo, GCS_BLOB_CONTENT.getBytes(StandardCharsets.UTF_8));
    createdBlobs.add(blobId);
    logger.info("Blob {} was created", blobId.getName());
    return blobId;
  }

  private Object blobRead() throws Exception {
    return testClient.get(makeTestBlobId());
  }

  private Object blobUpdate() throws Exception {
    Blob retrievedFile = testClient.get(makeTestBlobId());
    return retrievedFile.toBuilder().setContentType("text/html").build().update();
  }

  private Object blobDelete() throws Exception {
    boolean result = testClient.delete(makeTestBlobId());
    if (result) {
      testBlobId = null;
    }
    return result;
  }

  private Object bucketGet() throws Exception {
    return testClient.get(bucketName, Storage.BucketGetOption.fields(Storage.BucketField.values()));
  }

  private Object bucketDelete() throws Exception {
    return testClient.get(bucketName).delete();
  }

  // Make a blob for testing. We need a blob that can be read or deleted even if the
  // test user cannot create one. So we use our creatorTestUser to create the blob. We
  // reuse the blob, if it is still around. Note the variable clearing the blobDelete method.
  private BlobId makeTestBlobId() throws IOException {
    if (testBlobId == null) {
      Storage creatorClient = ClientTestUtils.getGcpStorageClient(creatorTestUser, projectId);
      testBlobId = blobCreate(creatorClient);
    }
    return testBlobId;
  }

  private String makeBlobName() {
    return RandomStringUtils.random(10, true, false);
  }
}
