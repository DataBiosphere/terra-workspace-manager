package bio.terra.workspace.app.configuration;

import bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils;
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
