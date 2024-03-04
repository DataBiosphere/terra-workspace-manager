package bio.terra.workspace.app;

import bio.terra.common.logging.LoggingInitializer;
import bio.terra.workspace.app.configuration.external.StartupConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from scanning.
      DataSourceAutoConfiguration.class,
    })
@ComponentScan(
    basePackages = {
      // Dependencies for Stairway
      "bio.terra.common.kubernetes",
      // Scan for logging-related components & configs
      "bio.terra.common.logging",
      // Scan for Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Scan for db components and configs
      "bio.terra.common.db",
      // Scan for iam token handling
      "bio.terra.common.iam",
      // Transaction management and DB retry configuration
      "bio.terra.common.retry.transaction",
      // Stairway initialization and status
      "bio.terra.common.stairway",
      // Scan for tracing-related components & configs
      "bio.terra.common.tracing",
      // Scan all policy service packages
      "bio.terra.policy",
      // Scan all service-specific packages beneath the current package
      "bio.terra.workspace",
      // Scan all landing zone service packages
      "bio.terra.landingzone"
    },
    // WSM has its own version of AuthenticatedUserRequest, AuthenticatedUserRequestFactory,
    // and ProxiedAuthenticatedUserRequest. So we need to exclude them
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "bio.terra.common.iam.*Authenticated.*")
    })
@EnableRetry
@EnableTransactionManagement
public class Main {
  public static void main(String[] args) {
    ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(Main.class).initializers(new LoggingInitializer()).run(args);

    // Exit after initialization, if requested.
    // This is used for debugging server startup issues within the github action context.
    // Normally we do not get the log for the local server startup in a GHA. This lets us
    // automate a test that will simply start the local server and exit.
    StartupConfiguration startupConfiguration = ctx.getBean(StartupConfiguration.class);
    System.out.printf(
        "Exit after initialization is: %s%n", startupConfiguration.isExitAfterInitialization());
    if (startupConfiguration.isExitAfterInitialization()) {
      System.out.println("Exit after initialization requested - exiting");
      ctx.close();
      System.out.println("Application Context closed");
      System.exit(0);
    }
  }
}
