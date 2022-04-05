package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.google.api.services.storagetransfer.v1.Storagetransfer;
import java.io.IOException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoragetransferConfiguration {

  @Bean
  public Storagetransfer getStoragetransfer() {
    try {
      return StorageTransferServiceUtils.createStorageTransferService();
    } catch (IOException e) {
      throw new BeanCreationException(e.getMessage());
    }
  }
}
