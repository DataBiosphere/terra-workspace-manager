package bio.terra.workspace.app.configuration.external;

import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.tracing")
public class TracingConfiguration implements InitializingBean {
  /** Rate of sampling, 0.0 - 1.0 */
  Double probability = null;

  public Double getProbability() {
    return probability;
  }

  public void setProbability(Double probability) {
    this.probability = probability;
  }

  /** Propagates the workspace.tracing.probability property to the OpenCensus tracing config. */
  @Override
  public void afterPropertiesSet() {
    TraceParams origParams = Tracing.getTraceConfig().getActiveTraceParams();
    Tracing.getTraceConfig()
        .updateActiveTraceParams(
            origParams.toBuilder()
                .setSampler(Samplers.probabilitySampler(getProbability()))
                .build());
  }
}
