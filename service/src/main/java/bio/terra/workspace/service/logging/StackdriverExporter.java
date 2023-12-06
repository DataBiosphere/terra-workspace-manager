package bio.terra.workspace.service.logging;

import bio.terra.workspace.service.features.FeatureService;
import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StackdriverExporter {

  private static final Logger logger = LoggerFactory.getLogger(StackdriverExporter.class);

  private final FeatureService featureService;

  @Autowired
  public StackdriverExporter(FeatureService featureService) {
    this.featureService = featureService;
  }

  @Bean(destroyMethod = "close")
  public PeriodicMetricReader metricReader() {
    if (!featureService.isFeatureEnabled(FeatureService.WSM_STACKDRIVER_EXPORTER_ENABLED)) {
      logger.info("Stackdriver exporter is not enabled, skip initializing.");
      return null;
    }

    return PeriodicMetricReader.create(GoogleCloudMetricExporter.createWithDefaultConfiguration());
  }
}
