package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;

public final class StorageTransferServiceUtils {
  private static final String APPLICATION_NAME = "terra-workspace-manager";

  private StorageTransferServiceUtils() {}

  public static Storagetransfer createStorageTransferService() throws IOException {
    GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(StoragetransferScopes.all());
    }

    return new Storagetransfer.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(credential))
        .setApplicationName(APPLICATION_NAME)
        .build();
  }
}
