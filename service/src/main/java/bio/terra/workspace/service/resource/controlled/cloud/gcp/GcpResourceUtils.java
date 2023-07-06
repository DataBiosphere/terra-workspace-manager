package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import bio.terra.workspace.service.crl.CrlService;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Bucket;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.HttpStatus;

public class GcpResourceUtils {

  /**
   * Try to fetch an existing bucket with the provided name. Because buckets are globally namespaced
   * a bucket may exist but be outside of the current project, where it may or may not be accessible
   * to WSM. There is no guarantee that the returned bucket exists in the workspace project.
   */
  public static Optional<Bucket> getBucket(String bucketName, CrlService crlService) {
    try {
      Storage wsmSaNakedStorageClient = crlService.createWsmSaNakedStorageClient();
      return Optional.of(wsmSaNakedStorageClient.buckets().get(bucketName).execute());
    } catch (GoogleJsonResponseException googleEx) {
      // If WSM doesn't have access to this bucket or it isn't found, return empty.
      if (googleEx.getStatusCode() == HttpStatus.SC_FORBIDDEN
          || googleEx.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return Optional.empty();
      }
      // Other errors from GCP are unexpected and should be rethrown.
      throw new RuntimeException("Error while looking up existing bucket project", googleEx);
    } catch (IOException e) {
      // Unexpected error, rethrow.
      throw new RuntimeException("Error while looking up existing bucket project", e);
    }
  }
}
