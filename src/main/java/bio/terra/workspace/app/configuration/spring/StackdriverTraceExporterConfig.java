package bio.terra.workspace.app.configuration.spring;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.AttributeValue;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configures the Stackdriver (aka Google Cloud Trace) exporter for OpenCensus, providing cloud
 * context and credentials, plus some fixed attributes that should show up on all traces sent by
 * this service.
 */
@Configuration
@ConditionalOnProperty("workspace.tracing.enabled")
public class StackdriverTraceExporterConfig {

  @Autowired
  public StackdriverTraceExporterConfig(Environment environment) {
    try {
      StackdriverTraceExporter.createAndRegister(
          StackdriverTraceConfiguration.builder()
              // Use Google's method for collecting the default project ID. This will attempt to
              // extract the project ID from environment variables and/or application default
              // credentials.
              .setProjectId(ServiceOptions.getDefaultProjectId())
              .setCredentials(GoogleCredentials.getApplicationDefault())
              .setFixedAttributes(
                  Map.of(
                      "terra/component",
                      AttributeValue.stringAttributeValue(
                          environment.getRequiredProperty("spring.application.name"))))
              .build());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
