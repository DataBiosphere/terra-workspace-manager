package bio.terra.workspace.azureDatabaseUtils.storage;

import bio.terra.workspace.azureDatabaseUtils.process.LaunchProcessException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import java.io.IOException;
import java.io.OutputStream;
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

  public OutputStream getBlobStorageUploadOutputStream(
      String blobName, String blobContainerName, String blobContainerUrlAuthenticated) {
    BlobContainerClient blobContainerClient =
        constructBlockBlobClient(blobContainerName, blobContainerUrlAuthenticated);
    // https://learn.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#upload-a-blob-via-an-outputstream
    return blobContainerClient.getBlobClient(blobName).getBlockBlobClient().getBlobOutputStream();
  }

  @Override
  public void streamInputFromBlobStorage(
      OutputStream toStream,
      String blobName,
      String blobContainerName,
      String blobContainerUrlAuthenticated) {
    BlobContainerClient blobContainerClient =
        constructBlockBlobClient(blobContainerName, blobContainerUrlAuthenticated);
    try (toStream) {
      blobContainerClient.getBlobClient(blobName).downloadStream(toStream);
    } catch (IOException ioEx) {
      throw new LaunchProcessException("Error streaming input to child process", ioEx);
    }
  }

  private BlobContainerClient constructBlockBlobClient(
      String blobContainerName, String blobContainerUrlAuthenticated) {
    // blobContainerUrlAuthenticated is a URL with a query parameter containing a SAS token

    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(blobContainerUrlAuthenticated).buildClient();

    try {
      // the way storage containers are set up in a workspace are as follows:
      // billing project gets a single azure storage account
      // each workspace gets a container inside of that storage account to keep its data
      return blobServiceClient.getBlobContainerClient(blobContainerName);
    } catch (BlobStorageException e) {
      // if the default workspace container doesn't exist, something went horribly wrong
      logger.error("Default storage container missing; could not find {}", blobContainerName);
      throw (e);
    }
  }
}
