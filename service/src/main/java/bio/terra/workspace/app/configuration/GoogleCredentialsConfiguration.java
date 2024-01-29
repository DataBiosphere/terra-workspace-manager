package bio.terra.workspace.app.configuration;

import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class GoogleCredentialsConfiguration {
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
   * We don't need ADC in unit tests, but we do need to provide something that looks like them in
   * unit tests.
   */
  @Bean
  @Profile("unit-test & azure")
  public GoogleCredentials getFakeGoogleCredentials() {
    return GoogleCredentials.newBuilder().build();
  }
}
