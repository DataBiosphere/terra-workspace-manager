package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;
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
 * expect many failures, we do not want to wait every time.
 */
public class GcsBucketAccessTester implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(GcsBucketAccessTester.class);
  private final TestUserSpecification creatorTestUser;
  private final String bucketName;
  private final String projectId;
  private BlobId testBlobId;
  private final List<BlobId> createdBlobs;
  private Storage testClient;
  private boolean needToWait;

  public GcsBucketAccessTester(
      TestUserSpecification creatorTestUser, String bucketName, String projectId) throws Exception {
    this.creatorTestUser = creatorTestUser;
    this.bucketName = bucketName;
    this.projectId = projectId;
    this.testBlobId = null;
    this.needToWait = false;
    this.createdBlobs = new ArrayList<>();

    // The creator user must be an EDITOR in order to configure this access tester
    // for testing another user's access. Ensure that the creator has valid access
    // before continuing.
    checkAccessWorker(creatorTestUser, ControlledResourceIamRole.EDITOR, true);
    logger.info(
        "User {} has permissions for role {}",
        creatorTestUser.userEmail,
        ControlledResourceIamRole.EDITOR);
  }

  @Override
  public void close() throws IOException {
    // Clean up any created blob so that delete happens quickly, lest the test timeout waiting.
    Storage creatorClient = ClientTestUtils.getGcpStorageClient(creatorTestUser, projectId);
    for (BlobId blobId : createdBlobs) {
      boolean found = creatorClient.delete(blobId);
      logger.info("Blob {} was {}", blobId.getName(), (found ? "found and deleted" : "not found"));
    }
  }

  /**
   * Check for access and wait for expected access to be allowed. We do not wait for access that is
   * expected to be denied, so this method cannot be used to wait for a user to lose access.
   *
   * @param testUser user to check
   * @param role the IamRole to test for; null to test the "user has no role" case
   * @throws Exception from asserts if the user has unexpected access
   */
  public void assertAccessWait(
      TestUserSpecification testUser, @Nullable ControlledResourceIamRole role) throws Exception {
    assertNotEquals(creatorTestUser, testUser);
    String result = checkAccessWorker(testUser, role, true);
    if (result != null) {
      fail("Access check failed: " + result);
    }
    logger.info("Access check of {} for role {} succeeded", testUser.userEmail, role);
  }

  /**
   * Check for access; do not wait for expected access to be allowed. Assert on unexpected access.
   *
   * @param testUser user to check
   * @param role the IamRole to test for; null to test the "user has no role" case
   * @throws Exception from asserts if the user has unexpected access
   */
  public void assertAccess(TestUserSpecification testUser, @Nullable ControlledResourceIamRole role)
      throws Exception {
    assertNotEquals(creatorTestUser, testUser);
    String result = checkAccessWorker(testUser, role, false);
    if (result != null) {
      fail("Access check failed: " + result);
    }
    logger.info("Access check of {} for role {} succeeded", testUser.userEmail, role);
  }

  /**
   * This method is intended for the negative check - waiting for a user to lose access to a role.
   * If a user has lost ANY of the permissions of a role, then we decide the user has lost the role.
   *
   * @param testUser user to check
   * @param role the IamRole to test for; null to test the "user has no role" case
   * @throws Exception from tests on an unexpected error
   */
  public void assertRemovedAccessWait(
      TestUserSpecification testUser, @Nullable ControlledResourceIamRole role) throws Exception {
    assertNotEquals(creatorTestUser, testUser);
    ClientTestUtils.getWithRetryOnFalse(() -> testRemovedAccess(testUser, role));
  }

  private Boolean testRemovedAccess(
      TestUserSpecification testUser, @Nullable ControlledResourceIamRole role) throws Exception {
    String result = checkAccessWorker(testUser, role, false);
    logger.info("User {} has permissions for role {}", testUser.userEmail, role);
    return (result == null);
  }

  /**
   * Worker method to test user access
   *
   * <p>NOTE: we never wait for a negative. If we are testing that user does not have a permission,
   * then we call the operation with doNoWait, regardless of the needToWait state.
   *
   * @param testUser user to check
   * @param role the IamRole to test for; null to test the "user has no role" case
   * @throws Exception from asserts if the user has unexpected access
   * @return On error, string describing the failed check. On success, null
   * @throws Exception unhandled failure of the operation (e.g., not a StorageException for
   *     permission failure)
   */
  private String checkAccessWorker(
      TestUserSpecification testUser, @Nullable ControlledResourceIamRole role, boolean wait)
      throws Exception {
    needToWait = wait;
    logger.info(
        "Checking access of {} for role {} with wait {}",
        testUser.userEmail,
        (role == null ? "none" : role.toString()),
        needToWait);

    testClient = ClientTestUtils.getGcpStorageClient(testUser, projectId);
    if (role == null) {
      if (doNoWait(() -> blobCreate(testClient))) {
        return "no role cannot create";
      }
      if (doNoWait(this::blobRead)) {
        return "no role cannot read";
      }
      if (doNoWait(this::blobUpdate)) {
        return "no role cannot update";
      }
      if (doNoWait(this::blobDelete)) {
        return "no role cannot delete";
      }
      if (doNoWait(this::bucketGet)) {
        return "no role cannot bucket get";
      }
      if (doNoWait(this::bucketDelete)) {
        return "no role cannot bucket delete";
      }
      return null; // Success
    }

    switch (role) {
      case READER -> {
        if (!doWithOptionalWait(this::blobRead)) {
          return "reader can read";
        }
        if (doNoWait(() -> blobCreate(testClient))) {
          return "reader cannot create";
        }
        if (doNoWait(this::blobUpdate)) {
          return "reader cannot update";
        }
        if (doNoWait(this::blobDelete)) {
          return "reader cannot delete";
        }
        if (doNoWait(this::bucketGet)) {
          return "reader cannot bucket get";
        }
        if (doNoWait(this::bucketDelete)) {
          return "reader cannot bucket delete";
        }
        return null; // Success
      }

      case WRITER -> {
        if (!doWithOptionalWait(() -> blobCreate(testClient))) {
          return "writer can create";
        }
        if (!doWithOptionalWait(this::blobRead)) {
          return "writer can read";
        }
        if (!doWithOptionalWait(this::blobUpdate)) {
          return "writer can update";
        }
        if (!doWithOptionalWait(this::blobDelete)) {
          return "writer can delete";
        }
        if (doNoWait(this::bucketGet)) {
          return "writer cannot bucket get";
        }
        if (doNoWait(this::bucketDelete)) {
          return "writer cannot bucket delete";
        }
        return null; // Success
      }

      case EDITOR -> {
        if (!doWithOptionalWait(() -> blobCreate(testClient))) {
          return "editor can create";
        }
        if (!doWithOptionalWait(this::blobRead)) {
          return "editor can read";
        }
        if (!doWithOptionalWait(this::blobUpdate)) {
          return "editor can update";
        }
        if (!doWithOptionalWait(this::blobDelete)) {
          return "editor can delete";
        }
        if (!doWithOptionalWait(this::bucketGet)) {
          return "editor can bucket get";
        }
        if (doNoWait(this::bucketDelete)) {
          return "editor cannot bucket delete";
        }
        return null; // Success
      }
      default -> throw new IllegalArgumentException("Invalid IAM role: " + role);
    }
  }

  // Common logic for optionally waiting on the first check.

  @FunctionalInterface
  public interface TestFunction {
    Object apply() throws Exception;
  }

  private boolean doWithOptionalWait(TestFunction function) throws Exception {
    if (needToWait) {
      return doWait(function);
    }
    return doNoWait(function);
  }

  private boolean doWait(TestFunction function) throws Exception {
    try {
      ClientTestUtils.getWithRetryOnException(function::apply);
      return true;
    } catch (StorageException e) {
      if (e.getCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
        assertEquals(HttpStatusCodes.STATUS_CODE_FORBIDDEN, e.getCode());
        return false;
      }
      throw e;
    }
  }

  private boolean doNoWait(TestFunction function) throws Exception {
    try {
      function.apply();
      return true;
    } catch (StorageException e) {
      if (e.getCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
        return false;
      }
      throw e;
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
