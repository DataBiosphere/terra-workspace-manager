package bio.terra.workspace.app;

import bio.terra.common.logging.LoggingInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from scanning.
      DataSourceAutoConfiguration.class,
    })
@ComponentScan(
    basePackages = {
      // Scan for logging-related components & configs
      "bio.terra.common.logging",
      // Scan for Liquibase migration components & configs
      "bio.terra.common.migrate",
      // Scan for tracing-related components & configs
      "bio.terra.common.tracing",
      // Scan all service-specific packages beneath the current package
      "bio.terra.workspace"
    })
@EnableScheduling
public class Main {
  public static void main(String[] args) {
    new SpringApplicationBuilder(Main.class).initializers(new LoggingInitializer()).run(args);
  }
}
