package bio.terra.workspace.service.resource.controlled.flight.clone.azure.managedIdentity;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.ControlledAzureManagedIdentityResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.azure.common.CloneControlledAzureResourceFlight;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

public class CloneControlledAzureManagedIdentityResourceFlight extends CloneControlledAzureResourceFlight<ControlledAzureManagedIdentityResource> {
  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureManagedIdentityResourceFlight.class);

  public CloneControlledAzureManagedIdentityResourceFlight(FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext, WsmResourceType.CONTROLLED_AZURE_MANAGED_IDENTITY);
  }

  // TODO: figure out MI-specific cloning

  @Override
  protected void copyDefinition(FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    var sourceIdentity =
            FlightUtils.getRequired(
                    inputParameters,
                    WorkspaceFlightMapKeys.ResourceKeys.RESOURCE,
                    ControlledAzureManagedIdentityResource.class);
    var userRequest =
            inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    var cloningInstructions =
            Optional.ofNullable(
                            inputParameters.get(
                                    WorkspaceFlightMapKeys.ResourceKeys.CLONING_INSTRUCTIONS,
                                    CloningInstructions.class))
                    .orElse(sourceIdentity.getCloningInstructions());

    RetryRule cloudRetry = RetryRules.cloud();
    addStep(new CopyAzureManagedIdentityDefinitionStep(
            flightBeanBag.getSamService(),
            userRequest,
            sourceIdentity,
            flightBeanBag.getControlledResourceService(),
            cloningInstructions
        ), cloudRetry);
  }

  @Override
  protected void copyResource(FlightBeanBag flightBeanBag, FlightMap inputParameters) {

  }

  @Override
  protected void copyReference(FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw new IllegalArgumentException("Cloning referenced azure managed identities not supported");
  }

  @Override
  protected void linkReference(FlightBeanBag flightBeanBag, FlightMap inputParameters) {
    throw new IllegalArgumentException("Cloning referenced azure managed identities not supported");
  }
}
