package bio.terra.workspace.app;

import bio.terra.common.logging.LoggingInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
      "bio.terra.workspace"
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
    new SpringApplicationBuilder(Main.class).initializers(new LoggingInitializer()).run(args);
  }
}
