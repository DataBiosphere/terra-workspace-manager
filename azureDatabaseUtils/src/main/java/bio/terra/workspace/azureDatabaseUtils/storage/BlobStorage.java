package bio.terra.workspace.azureDatabaseUtils.storage;

import java.io.OutputStream;

/* Allow for blob storage mocking. */
public interface BlobStorage {
  OutputStream getBlobStorageUploadOutputStream(
      String blobName, String blobContainerName, String blobContainerUrlAuthenticated);

  default void streamInputFromBlobStorage(
      OutputStream toStream, String blobName, String blobContainerName, String authToken) {}
}
