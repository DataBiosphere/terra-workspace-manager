package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.UUID;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT;

public class CreateDbDumpEncryptionKeyStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateDbDumpEncryptionKeyStep.class);


  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {

    KeyGenerator kg;
    try {
      kg = KeyGenerator.getInstance("AES");
    } catch (NoSuchAlgorithmException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    kg.init(256, new SecureRandom());
    SecretKey secretKey = kg.generateKey();
    String encodedString = java.util.Base64.getEncoder().encodeToString(secretKey.getEncoded());

    context.getWorkingMap().put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMP_ENCRYPTION_KEY, encodedString);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo in this step (dump file will be cleaned up by storage container deletion)
    return StepResult.getStepResultSuccess();
  }
}
