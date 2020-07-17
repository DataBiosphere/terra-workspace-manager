package bio.terra.workspace.service.trace;

import bio.terra.workspace.app.configuration.StackdriverConfiguration;
import bio.terra.workspace.common.exception.StackdriverRegistrationException;
import bio.terra.workspace.common.utils.GoogleUtils;
import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StackdriverTrace {
  private final StackdriverConfiguration stackdriverConfiguration;
  private static Sampler sampler;


  @Autowired
  public StackdriverTrace(StackdriverConfiguration stackdriverConfiguration) {
    this.stackdriverConfiguration = stackdriverConfiguration;
    this.sampler = Samplers.alwaysSample();
    // Samplers.probabilitySampler(stackdriverConfiguration.getSamplingProbability());
    // Creates and registers the exporter. This must be happen each time WSM is instantiated.
    createAndRegister();
  }

  public static final Tracer tracer = Tracing.getTracer();
  private final List<String> traceScopes =
      Arrays.asList("https://www.googleapis.com/auth/trace.append");

  private void createAndRegister() {
    try {
      StackdriverTraceConfiguration conf =
          StackdriverTraceConfiguration.builder()
              .setProjectId(stackdriverConfiguration.getProjectId())
              .setCredentials(
                  GoogleUtils.getGoogleCredentials(
                      stackdriverConfiguration.getServiceAccountFilePath(), traceScopes))
              .build();
      StackdriverTraceExporter.createAndRegister(conf);
    } catch (IOException e) {
      throw new StackdriverRegistrationException(
          "Failed to create and register OpenCensus Stackdriver Trace exporter", e);
    }
  }

  public Scope scope(String traceName) {
    return tracer.spanBuilder(traceName).setSampler(sampler).startScopedSpan();
  }

  public void annotate(String annotation) {
    tracer.getCurrentSpan().addAnnotation(annotation);
  }
}
