package bio.terra.workspace.app.configuration;

import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoragetransferConfiguration {
  @Bean
  public Storagetransfer getStoragetransfer(GoogleCredentials googleCredentials) {
    return new Storagetransfer.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(googleCredentials))
        .setApplicationName(StorageTransferServiceUtils.APPLICATION_NAME)
        .build();
  }
}
