package bio.terra.workspace.service.trace;

import bio.terra.workspace.app.configuration.StackdriverConfiguration;
import bio.terra.workspace.common.exception.StackdriverRegistrationException;
import com.google.auth.oauth2.GoogleCredentials;
import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StackdriverTrace {
  private final StackdriverConfiguration stackdriverConfiguration;

  @Autowired
  public StackdriverTrace(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
  }

  private final Logger logger = LoggerFactory.getLogger(StackdriverTrace.class);
  private static final Tracer tracer = Tracing.getTracer();
  private final List<String> traceScopes =
      Arrays.asList("https://www.googleapis.com/auth/trace.append");

  // probably belongs in a util class
  public GoogleCredentials getGoogleCredentials() throws IOException {
    return GoogleCredentials.fromStream(
            new ByteArrayInputStream(
                Files.readAllBytes(
                    new File(stackdriverConfiguration.getServiceAccountFilePath()).toPath())))
        .createScoped(traceScopes);
  }

  public void createAndRegister() {
    try {
      StackdriverTraceConfiguration conf =
          StackdriverTraceConfiguration.builder()
              .setProjectId(stackdriverConfiguration.getProjectId())
              .setCredentials(getGoogleCredentials())
              .build();
      StackdriverTraceExporter.createAndRegister(conf);
    } catch (IOException e) {
      throw new StackdriverRegistrationException(
          "Failed to create and register OpenCensus Stackdriver Trace exporter", e);
    }
  }

  public Scope scope(String traceName) {
    return tracer.spanBuilder(traceName).setSampler(
            Samplers.probabilitySampler(stackdriverConfiguration.getSamplingProbability()))
            .startScopedSpan();
  }


  public void annotate(String annotation) {
    tracer.getCurrentSpan().addAnnotation(annotation);
  }
}
