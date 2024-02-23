package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;

/**
 * TODO: explain and document the reason for this and its value fully (also GetWorkspaceManagedIdentityForDeleteStep)
 *
 *   A simple type extension for GetPetManagedIdentityStep, to type as a DeleteControlledResourceStep
 *   This interface and the associated typing of  ControlledResource.getDeleteSteps() may seem a bit excessive at first,
 *   but .....
 *
 *   This creates the occasional artifact like this, but enhances overall discoverability, making it harder to implement
 *   a new controlled resource without thinking about common handling of DeleteControlledResourceStep
 *
 *   but it does indicate it's used for delete, which is useful in itself
 *   and this is not just for typing - it means we could put in some different error handling or something
 *   also, any solution is going to require *something* to be shoe-horned in, and this isn't a bad one
 *
 */
public class GetPetManagedIdentityForDeleteStep extends GetPetManagedIdentityStep implements DeleteControlledResourceStep {

  public GetPetManagedIdentityForDeleteStep(
    AzureConfiguration azureConfig,
    CrlService crlService,
    SamService samService,
    String userEmail) {
    super(azureConfig, crlService, samService, userEmail);
  }


}
