package scripts.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ReferencedGcpResourceApi;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateGcpGcsObjectReferenceRequestBody;
import bio.terra.workspace.model.GcpGcsObjectAttributes;
import bio.terra.workspace.model.GcpGcsObjectResource;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcsBucketObjectUtils {

  private static final Logger logger = LoggerFactory.getLogger(GcsBucketObjectUtils.class);
  private static final Pattern GCS_OBJECT_PATTERN = Pattern.compile("^gs://([^/]+)/(.+)$");

  public static Blob retrieveBucketFile(
      String bucketName, String gcpProjectId, TestUserSpecification bucketReader) throws Exception {
    Storage cloningUserStorageClient =
        ClientTestUtils.getGcpStorageClient(bucketReader, gcpProjectId);
    BlobId blobId = BlobId.of(bucketName, GcsBucketUtils.GCS_BLOB_NAME);

    final Blob retrievedFile =
        ClientTestUtils.getWithRetryOnException(() -> cloningUserStorageClient.get(blobId));
    logger.info("Retrieved file {} from bucket {}", GcsBucketUtils.GCS_BLOB_NAME, bucketName);
    assertNotNull(retrievedFile);
    assertEquals(blobId.getName(), retrievedFile.getBlobId().getName());
    return retrievedFile;
  }

  /**
   * Calls WSM to create a referenced GCS bucket object in the specified workspace.
   *
   * <p>This method retries on all WSM exceptions, do not use it for the negative case (where you do
   * not expect a user to be able to create a reference).
   */
  public static GcpGcsObjectResource makeGcsObjectReference(
      GcpGcsObjectAttributes file,
      ReferencedGcpResourceApi resourceApi,
      UUID workspaceUuid,
      String name,
      @Nullable CloningInstructionsEnum cloningInstructionsEnum)
      throws Exception {
    var body =
        new CreateGcpGcsObjectReferenceRequestBody()
            .metadata(
                CommonResourceFieldsUtil.makeReferencedResourceCommonFields(
                    name,
                    Optional.ofNullable(cloningInstructionsEnum)
                        .orElse(CloningInstructionsEnum.NOTHING)))
            .file(file);

    logger.info("Making reference to a gcs bucket file");
    return ClientTestUtils.getWithRetryOnException(
        () -> resourceApi.createGcsObjectReference(body, workspaceUuid));
  }

  /**
   * Parse GCS object attributes from a GCS URI (e.g. "gs://my-bucket/somedir/myobject"). This will
   * return the full name of the object ("somedir/myobject" above), as GCS buckets don't actually
   * have directory structure.
   *
   * <p>This only parses the resource identifier string, it does not check if the provided IDs are
   * real or valid.
   */
  public static GcpGcsObjectAttributes parseGcsObject(String resourceIdentifier) {
    Matcher matcher = GCS_OBJECT_PATTERN.matcher(resourceIdentifier);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Resource identifier "
              + resourceIdentifier
              + " does not match expected pattern for GCS bucket");
    }
    return new GcpGcsObjectAttributes().bucketName(matcher.group(1)).fileName(matcher.group(2));
  }
}
