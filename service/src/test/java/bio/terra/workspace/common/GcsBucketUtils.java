package bio.terra.workspace.common;

import static bio.terra.workspace.common.GcpCloudUtils.getWithRetryOnException;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.storage.BlobCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GcsBucketUtils {

  public static final String GCS_FILE_NAME = "foo";
  public static final String GCS_FILE_CONTENTS = "bar";
  private static final Logger LOGGER = LoggerFactory.getLogger(GcsBucketUtils.class);
  private static final String TSV_FILE_NAME = "signed_url_list.tsv";

  @Autowired CrlService crlService;

  public static Blob getObjectMetadata(String projectId, String bucketName, String blobName) {
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

    return storage.get(
        bucketName, blobName, Storage.BlobGetOption.fields(Storage.BlobField.values()));
  }

  /**
   * Signing a URL requires Credentials which implement ServiceAccountSigner. These can be set
   * explicitly using the Storage.SignUrlOption.signWith(ServiceAccountSigner) option. If you don't,
   * you could also pass a service account signer to StorageOptions, i.e.
   * StorageOptions().newBuilder().setCredentials(ServiceAccountSignerCredentials). In this example,
   * neither of these options are used, which means the following code only works when the
   * credentials are defined via the environment variable GOOGLE_APPLICATION_CREDENTIALS, and those
   * credentials are authorized to sign a URL. See the documentation for Storage.signUrl for more
   * details.
   */
  public static URL generateV4GetObjectSignedUrl(
      String projectId, String bucketName, String objectName) throws StorageException {

    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

    // Define resource
    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();

    return storage.signUrl(blobInfo, 12, TimeUnit.HOURS, Storage.SignUrlOption.withV4Signature());
  }

  /**
   * Build an url list following this format:
   * https://cloud.google.com/storage-transfer/docs/create-url-list.
   */
  public static String buildUrlListTsv(String projectId, String bucketName, List<String> objects) {
    StringBuilder builder = new StringBuilder("TsvHttpData-1.0\n");
    for (var object : objects) {
      Blob blob = getObjectMetadata(projectId, bucketName, object);
      Long size = blob.getSize();
      String md5 = blob.getMd5();
      var signedUrl = generateV4GetObjectSignedUrl(projectId, bucketName, object).toString();
      builder.append(signedUrl);
      builder.append("\t");
      builder.append(size);
      builder.append("\t");
      builder.append(md5);
      builder.append("\n");
    }
    return builder.toString();
  }

  /**
   * Create signed urls for each objects and create an url list tsv file. Then upload the tsv file
   * to the bucket.
   *
   * @return return a signed url of the tsv file.
   */
  public static URL buildSignedUrlListObject(
      GoogleCredentials userCredential, String projectId, String bucketName, List<String> objects)
      throws Exception {
    String fileContent = buildUrlListTsv(projectId, bucketName, objects);
    LOGGER.info("File {}: {}", TSV_FILE_NAME, fileContent);
    addFileToBucket(userCredential, projectId, bucketName, TSV_FILE_NAME, fileContent);

    return generateV4GetObjectSignedUrl(projectId, bucketName, TSV_FILE_NAME);
  }

  public static void addFileToBucket(
      GoogleCredentials userCredential, String projectId, String bucketName) throws Exception {
    addFileToBucket(userCredential, projectId, bucketName, GCS_FILE_NAME, GCS_FILE_CONTENTS);
  }

  public static void addFileToBucket(
      GoogleCredentials userCredential,
      String projectId,
      String bucketName,
      String fileName,
      String fileContent)
      throws Exception {
    Storage storageClient = getGcpStorageClient(userCredential, projectId);
    BlobId blobId = BlobId.of(bucketName, fileName);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

    // Create a blob with retry to allow permission propagation
    RetryUtils.getWithRetryOnException(
        () -> storageClient.create(blobInfo, fileContent.getBytes(StandardCharsets.UTF_8)));
  }

  public static void waitForProjectAccess(GoogleCredentials userCredential, String projectId)
      throws Exception {
    Storage storage = getGcpStorageClient(userCredential, projectId);
    getWithRetryOnException(() -> testIam(storage));
  }

  /** Asserts bucket has file as per addFileToBucket(). */
  public static void assertBucketFileFooContainsBar(
      GoogleCredentials userCredential, String projectId, String bucketName) {
    Storage storageClient = getGcpStorageClient(userCredential, projectId);
    String actualContents =
        new String(storageClient.readAllBytes(bucketName, GCS_FILE_NAME), StandardCharsets.UTF_8);
    assertEquals(GCS_FILE_CONTENTS, actualContents);
  }

  public static void assertBucketFiles(
      GoogleCredentials userCredential,
      String projectId,
      String bucketName,
      String fileName,
      String fileContent)
      throws Exception {
    Storage storageClient = getGcpStorageClient(userCredential, projectId);
    String actualContents =
        RetryUtils.getWithRetryOnException(
            () ->
                new String(
                    storageClient.readAllBytes(bucketName, fileName), StandardCharsets.UTF_8));
    assertEquals(fileContent, actualContents);
  }

  /**
   * Asserts that the bucket is empty. Retries for 5 minutes to accommodate permission propagation
   * delay
   */
  public void assertBucketHasNoFiles(
      AuthenticatedUserRequest userRequest, String projectId, String bucketName) throws Exception {
    StorageCow storageCow = crlService.createStorageCow(projectId, userRequest);
    int numFiles =
        RetryUtils.getWithRetryOnException(
            () -> countFiles(storageCow, bucketName),
            /* totalDuration= */ Duration.ofMinutes(5),
            /* initialSleep= */ Duration.ofSeconds(5),
            /* factorIncrease= */ 1.0,
            /* sleepDurationMax= */ Duration.ofSeconds(30),
            null);
    assertEquals(0, numFiles);
  }

  private int countFiles(StorageCow storageCow, String bucketName) {
    int numFiles = 0;
    for (BlobCow blob : storageCow.get(bucketName).list().iterateAll()) {
      numFiles++;
    }
    return numFiles;
  }

  public void assertBucketAttributes(
      AuthenticatedUserRequest userRequest,
      String projectId,
      String bucketName,
      String expectedLocation,
      ApiGcpGcsBucketDefaultStorageClass expectedStorageClass,
      List<LifecycleRule> expectedLifecycleRules)
      throws Exception {
    StorageCow storageCow = crlService.createStorageCow(projectId, userRequest);
    BucketInfo actualBucketInfo =
        RetryUtils.getWithRetryOnException(() -> storageCow.get(bucketName).getBucketInfo());

    assertThat(expectedLocation, equalToIgnoringCase(actualBucketInfo.getLocation()));
    assertEquals(expectedStorageClass.name(), actualBucketInfo.getStorageClass().name());
    assertThat(
        actualBucketInfo.getLifecycleRules(), containsInAnyOrder(expectedLifecycleRules.toArray()));
  }

  private static Boolean testIam(Storage storage) {
    storage.list();
    return true;
  }

  private static Storage getGcpStorageClient(GoogleCredentials userCredential, String projectId) {
    return StorageOptions.newBuilder()
        .setCredentials(userCredential)
        .setProjectId(projectId)
        .build()
        .getService();
  }
}
