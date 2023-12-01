package bio.terra.workspace.azureDatabaseUtils;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class AzureDatabaseUtilsApplication {

  static {
    // Add bouncy castle (FIPS) provider:
    java.security.Security.addProvider(new org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider());
  }

  public static void main(String[] args) {
    new SpringApplicationBuilder(AzureDatabaseUtilsApplication.class).run(args);
  }
}
