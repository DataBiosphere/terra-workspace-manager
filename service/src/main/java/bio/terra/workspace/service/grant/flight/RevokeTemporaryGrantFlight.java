package bio.terra.workspace.service.grant.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import java.util.UUID;

/**
 * This flight revokes one temporary grant. There are three steps:
 *
 * <ul>
 *   <le> step 1 - lock the grant</le>
 *   <p><le> step 2 - revoke the grant</le>
 *   <p><le> step 3 - unlock the grant</le>
 * </ul>
 */
public class RevokeTemporaryGrantFlight extends Flight {
  public static final String SKIP = "skip";

  public static final String GRANT_ID_KEY = "grantId";

  public RevokeTemporaryGrantFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    var dbRetry = RetryRules.shortDatabase();

    UUID grantId = inputParameters.get(GRANT_ID_KEY, UUID.class);

    addStep(new LockGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
    // output of the step in the working map is:
    //  skip - if the lock failed, skip Boolean is set true and the steps do nothing.

    addStep(
        new RevokeStep(
            flightBeanBag.getGcpCloudContextService(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGrantDao(),
            flightBeanBag.getControlledResourceService(),
            grantId));

    addStep(new DeleteGrantStep(flightBeanBag.getGrantDao(), grantId), dbRetry);
  }
}
