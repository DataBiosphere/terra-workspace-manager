package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.service.buffer.BufferService;
import org.springframework.context.ApplicationContext;

// NOTE: DO NOT USE. Currently just a shell class to exercise connection to Buffer Service.
public class CreateGoogleContextRBSFlight extends Flight {
  public CreateGoogleContextRBSFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    BufferService bufferService = appContext.getBean(BufferService.class);
    CloudResourceManagerCow resourceManager = appContext.getBean(CloudResourceManagerCow.class);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 5 * 60,
            /* maxOperationTimeSeconds= */ 16);
    addStep(new GenerateResourceIdStep());
    addStep(new PullProjectFromPoolStep(bufferService, resourceManager), retryRule);
  }
}
