package bio.terra.workspace.azureDatabaseUtils;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AzureDatabaseUtilsApplication {
  public static void main(String[] args) {
    new SpringApplicationBuilder(AzureDatabaseUtilsApplication.class).run(args);
  }
}
