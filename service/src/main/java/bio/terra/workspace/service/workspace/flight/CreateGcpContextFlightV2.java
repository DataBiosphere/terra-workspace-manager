package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.common.utils.WsmFlight;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/**
 * A {@link Flight} for creating a Google cloud context for a workspace using Buffer Service to
 * create the project. This is V2 of the flight. It is a separate version so that we do not break a
 * running V1 of the flight during an upgrade.
 *
 * <p>This flight includes two changes:
 *
 * <ol>
 *   <li>Cloud context locking. We write the cloud context row into the database in an incomplete
 *       form to prevent concurrent, conflicting cloud context creations
 *   <li>Store sync'd workspace policy groups in the cloud context. These fixed group names can be
 *       reused from WSM data rather than requesting them from Sam during controlled resource
 *       created.
 * </ol>
 *
 * In order to add items to the cloud context, a version 2 of the serialized cloud context form is
 * used.
 */
public class CreateGcpContextFlightV2 extends WsmFlight {

  public CreateGcpContextFlightV2(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    // Extract the input parameters we need
    UUID workspaceUuid =
        UUID.fromString(getInputRequired(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    AuthenticatedUserRequest userRequest =
        getInputRequired(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Common step generation, shared with clone
    beanBag().getGcpCloudContextService().makeCreateGcpContextSteps(
        this, workspaceUuid, userRequest);
  }
}
