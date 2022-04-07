package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class StoragetransferConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(StoragetransferConfiguration.class);

  @Bean
  @Profile("!unit-test")
  public GoogleCredentials getGoogleCredentials() {
    try {
      GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
      if (googleCredentials.createScopedRequired()) {
        googleCredentials = googleCredentials.createScoped(StoragetransferScopes.all());
      }
      return googleCredentials;

    } catch (IOException e) {
      throw new BeanCreationException(e.getMessage());
    }
  }

  /**
   * We don't need ADC in unit tests, but we do need to provide something that looks like them
   * in unit tests.
   */
  @Bean
  @Profile("unit-test")
  public GoogleCredentials getFakeGoogleCredentials() {
    return GoogleCredentials.newBuilder().build();
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
