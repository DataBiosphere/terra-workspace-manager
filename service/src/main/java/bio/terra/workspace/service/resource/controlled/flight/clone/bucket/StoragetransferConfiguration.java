package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoragetransferConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(StoragetransferConfiguration.class);

  @Bean
  public GoogleCredentials getGoogleCredentials() {
    try {
      GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
      if (googleCredentials.createScopedRequired()) {
        googleCredentials = googleCredentials.createScoped(StoragetransferScopes.all());
      }
      return googleCredentials;

    } catch (IOException e) {
      logger.warn(
          "Failed to get ADC. This is expected in unit tests. Returning empty instance.", e);
      //      throw new BeanCreationException(e.getMessage());
      return GoogleCredentials.newBuilder().build();
    }
  }

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
