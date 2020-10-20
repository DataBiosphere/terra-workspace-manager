package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.configuration.external.TracingConfiguration;
import com.google.auth.oauth2.ServiceAccountCredentials;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.AttributeValue;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty("workspace.tracing.enabled")
public class StackdriverTraceExporterConfig {

  @Autowired
  public StackdriverTraceExporterConfig(
      TracingConfiguration tracingConfiguration, Environment environment) {
    try {
      StackdriverTraceExporter.createAndRegister(
          StackdriverTraceConfiguration.builder()
              .setProjectId(tracingConfiguration.getProjectId())
              .setCredentials(
                  ServiceAccountCredentials.fromStream(
                      new FileInputStream(tracingConfiguration.getSaPath())))
              .setFixedAttributes(
                  Map.of(
                      "/component",
                      AttributeValue.stringAttributeValue(
                          environment.getRequiredProperty("spring.application.name"))))
              .build());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
