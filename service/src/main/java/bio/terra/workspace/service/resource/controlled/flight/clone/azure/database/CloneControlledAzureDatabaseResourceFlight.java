package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.CreateAzureDatabaseStep;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.CloneControlledAzureResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.StepRetryRulePair;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneControlledAzureDatabaseResourceFlight extends CloneControlledAzureResourceFlight {

  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureDatabaseResourceFlight.class);

  public CloneControlledAzureDatabaseResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
  }

  @Override
  protected List<StepRetryRulePair> copyDefinition(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceDatabase =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledAzureDatabaseResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    var cloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                    CloningInstructions.class))
            .orElse(sourceDatabase.getCloningInstructions());

    return List.of(
        new StepRetryRulePair(
            new CopyControlledAzureDatabaseDefinitionStep(
                flightBeanBag.getSamService(),
                userRequest,
                sourceDatabase,
                flightBeanBag.getControlledResourceService(),
                cloningInstructions),
            RetryRules.cloud()));
  }

  @Override
  protected List<StepRetryRulePair> copyResource(
      FlightBeanBag flightBeanBag, FlightMap inputParameters) {

    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    var sourceDatabase =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
            ControlledAzureDatabaseResource.class);

    return List.of(
        new StepRetryRulePair(
            new DumpAzureDatabaseStep(
                sourceDatabase,
                flightBeanBag.getLandingZoneApiDispatch(),
                flightBeanBag.getSamService(),
                flightBeanBag.getWorkspaceService(),
                flightBeanBag.getAzureStorageAccessService(),
                flightBeanBag.getAzureDatabaseUtilsRunner()),
            RetryRules.cloud()),
        new StepRetryRulePair(
            new CreateAzureDatabaseStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag.getCrlService(),
                sourceDatabase,
                flightBeanBag.getLandingZoneApiDispatch(),
                flightBeanBag.getSamService(),
                flightBeanBag.getWorkspaceService(),
                destinationWorkspaceId,
                flightBeanBag.getAzureDatabaseUtilsRunner()),
            RetryRules.cloud()),
        new StepRetryRulePair(
            new RestoreAzureDatabaseStep(
                flightBeanBag.getLandingZoneApiDispatch(),
                flightBeanBag.getSamService(),
                flightBeanBag.getWorkspaceService(),
                flightBeanBag.getAzureStorageAccessService(),
                flightBeanBag.getAzureDatabaseUtilsRunner()),
            RetryRules.cloud()));
  }
}
