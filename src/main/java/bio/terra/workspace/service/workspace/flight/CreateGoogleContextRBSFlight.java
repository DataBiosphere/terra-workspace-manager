package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.crl.CrlService;
import org.springframework.context.ApplicationContext;

// NOTE: DO NOT USE. Currently just a shell class to exercise connection to Buffer Service.
public class CreateGoogleContextRBSFlight extends Flight {
  public CreateGoogleContextRBSFlight(FlightMap inputParameters, Object applicationContext)
      throws Exception {
    super(inputParameters, applicationContext);
    ApplicationContext appContext = (ApplicationContext) applicationContext;
    BufferService bufferService = appContext.getBean(BufferService.class);
    CrlService crl = appContext.getBean(CrlService.class);

    RetryRule retryRule =
        new RetryRuleExponentialBackoff(
            /* initialIntervalSeconds= */ 1,
            /* maxIntervalSeconds= */ 5 * 60,
            /* maxOperationTimeSeconds= */ 16);
    addStep(new GenerateResourceIdStep());
    addStep(new PullProjectFromPoolStep(bufferService, crl.cloudResourceManagerCow()), retryRule);
  }
}
