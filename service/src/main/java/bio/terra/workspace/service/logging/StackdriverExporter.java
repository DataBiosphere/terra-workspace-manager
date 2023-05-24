package bio.terra.workspace.service.logging;

import bio.terra.workspace.service.features.FeatureService;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StackdriverExporter {

  private static final Logger logger = LoggerFactory.getLogger(StackdriverExporter.class);

  private final FeatureService featureService;

  @Autowired
  public StackdriverExporter(FeatureService featureService) {
    this.featureService = featureService;
  }

  public void initialize() {
    if (!featureService.stackdriverExporterEnabled()) {
      logger.info("Stackdriver exporter is not enabled, skip initializing.");
      return;
    }
    try {
      StackdriverStatsExporter.createAndRegister();
    } catch (IOException e) {
      logger.error("Unable to initialize Stackdriver stats exporting.", e);
    }
  }
}
