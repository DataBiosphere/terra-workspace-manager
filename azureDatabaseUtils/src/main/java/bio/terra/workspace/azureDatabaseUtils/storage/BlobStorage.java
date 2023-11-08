package bio.terra.workspace.azureDatabaseUtils.storage;

import java.io.InputStream;
import java.io.OutputStream;

/* Allow for blob storage mocking. */
public interface BlobStorage {
  default void streamOutputToBlobStorage(
      InputStream fromStream,
      String blobName,
      String blobContainerName,
      String blobContainerUrlAuthenticated) {}

  default void streamInputFromBlobStorage(
      OutputStream toStream, String blobName, String blobContainerName, String authToken) {}

  default void deleteBlob(String blobFile, String blobContainerName, String authToken) {}
}
