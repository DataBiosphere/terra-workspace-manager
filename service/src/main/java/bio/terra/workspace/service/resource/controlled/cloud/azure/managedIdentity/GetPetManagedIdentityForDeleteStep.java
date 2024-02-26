package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;

/**
 * A simple type extension for GetPetManagedIdentityStep, to type as a DeleteControlledResourceStep.
 * TODO: figure out if this needs functionality from DeleteControlledAzureResourceStep (TBD,
 * WOR-787), and adjust as needed
 */
// FIXME: I think the name difference could cause issues if we interrupt a flight on upgrade
public class GetPetManagedIdentityForDeleteStep extends GetPetManagedIdentityStep
    implements DeleteControlledResourceStep {

  public GetPetManagedIdentityForDeleteStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      String userEmail) {
    super(azureConfig, crlService, samService, userEmail);
  }
}
