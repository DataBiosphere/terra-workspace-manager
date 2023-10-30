package bio.terra.workspace.azureDatabaseUtils.storage;

import bio.terra.workspace.azureDatabaseUtils.process.LaunchProcessException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBlobStorage implements BlobStorage {
  public AzureBlobStorage() {}

  private static final Logger logger = LoggerFactory.getLogger(AzureBlobStorage.class);

  /*
  6/28/2023 - Terra's storage inside a billing project is organized as follows:
  by default a billing project gets a single blob storage azure resource
  each workspace that gets created inside of that billing project will get its own container inside the blob storage
  the container will follow the naming of "sc-<workspaceId>"
  all files that are uploaded into the workspace will be inside the appropriate workspace container
  containers are restricted to have the same access as the users who have access to the workspace

  backups will be stored inside the workspace container in a path determined by function GenerateBackupFilename
  which exists within BackupService.java.
   */
  @Override
  public void streamOutputToBlobStorage(
      InputStream fromStream,
      String blobName,
      String workspaceId,
      String blobContainerName,
      String blobstorageDetails) {
    BlobContainerClient blobContainerClient =
        constructBlockBlobClient(workspaceId, blobContainerName, blobstorageDetails);
    // https://learn.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#upload-a-blob-via-an-outputstream
    try (BufferedOutputStream blobOS =
        new BufferedOutputStream(
            blobContainerClient
                .getBlobClient(blobName)
                .getBlockBlobClient()
                .getBlobOutputStream())) {
      try (BufferedReader bufferedReader =
          new BufferedReader(new InputStreamReader(fromStream, StandardCharsets.UTF_8))) {
        int line;
        while ((line = bufferedReader.read()) != -1) {
          blobOS.write(line);
        }
      }
    } catch (IOException ioEx) {
      throw new LaunchProcessException("Error streaming output of child process", ioEx);
    }
  }

  @Override
  public void streamInputFromBlobStorage(
      OutputStream toStream,
      String blobName,
      String workspaceId,
      String blobContainerName,
      String blobstorageDetails) {
    BlobContainerClient blobContainerClient =
        constructBlockBlobClient(workspaceId, blobContainerName, blobstorageDetails);
    try (toStream) {
      blobContainerClient.getBlobClient(blobName).downloadStream(toStream);
    } catch (IOException ioEx) {
      throw new LaunchProcessException("Error streaming input to child process", ioEx);
    }
  }

  private BlobContainerClient constructBlockBlobClient(
      String workspaceId, String blobContainerName, String blobstorageDetails) {
    // TODO: determine where blobstorageDetails should come from.
    // This implementation is 95% copied from WDS's, in which they call WSM's APIs to retrieve a url
    // with an included SAS token.
    // The implementation here assumes that this url+token will be passed in by WSM itself when it
    // ultimately calls the pgDump function.

    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(blobstorageDetails).buildClient();

    try {
      // the way storage containers are set up in a workspace are as follows:
      // billing project gets a single azure storage account
      // each workspace gets a container inside of that storage account to keep its data
      return blobServiceClient.getBlobContainerClient(blobContainerName);
    } catch (BlobStorageException e) {
      // if the default workspace container doesn't exist, something went horribly wrong
      logger.error("Default storage container missing for workspace id {}", workspaceId);
      throw (e);
    }
  }

  @Override
  public void deleteBlob(
      String blobFile, String workspaceId, String blobContainerName, String authToken) {
    BlobContainerClient blobContainerClient =
        constructBlockBlobClient(workspaceId, blobContainerName, authToken);
    try {
      var blobClient = blobContainerClient.getBlobClient(blobFile);
      blobClient.delete();
    } catch (BlobStorageException e) {
      logger.error("Failed to delete file with name {}. ", blobFile);
      throw (e);
    }
  }
}
