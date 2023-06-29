package bio.terra.workspace.azureCreateDb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class CreateAzureDatabaseApplication {
  @Value("${azureCreateDb.newDbUserOid}")
  private String newDbUserOid;

  @Value("${azureCreateDb.newDbUserName}")
  private String newDbUserName;

  @Value("${azureCreateDb.newDbName}")
  private String newDbName;

  public static void main(String[] args) {
    new SpringApplicationBuilder(CreateAzureDatabaseApplication.class).run(args);
  }

  @Profile("!test")
  @Bean
  public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
    return args -> {
      ctx.getBean(CreateDatabaseService.class).createDatabase(newDbName, newDbUserName, newDbUserOid);
    };
  }
}
