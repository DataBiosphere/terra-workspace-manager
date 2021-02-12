package bio.terra.workspace.app;

import bio.terra.common.logging.LoggingInitializer;
import io.opencensus.contrib.spring.autoconfig.OpenCensusAutoConfiguration;
import io.opencensus.contrib.spring.autoconfig.TraceWebAsyncClientAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from scanning.
      DataSourceAutoConfiguration.class,
      // If these two classes are not excluded, then a TraceWebAsyncClientAutoConfiguration
      // bean must be provided even though it's not needed for this application.
      OpenCensusAutoConfiguration.class,
      TraceWebAsyncClientAutoConfiguration.class
    })
@ComponentScan(
    basePackages = {
      // Scan all service-specific packages beneath the workspace namespace
      "bio.terra.workspace",
      // Load logging-related components and configs from the Terra common library
      "bio.terra.common.logging"
    })
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"bio.terra.workspace", "bio.terra.common.migrate"})
@EnableScheduling
public class Main {
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(Main.class);
    app.addInitializers(new LoggingInitializer());
    app.run(args);
  }
}
