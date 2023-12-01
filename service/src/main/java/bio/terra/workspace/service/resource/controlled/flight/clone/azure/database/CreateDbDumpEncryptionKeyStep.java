package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class CreateDbDumpEncryptionKeyStep implements Step {
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

    context
        .getWorkingMap()
        .put(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMP_ENCRYPTION_KEY,
            encodedString);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo in this step (dump file will be cleaned up by storage container deletion)
    return StepResult.getStepResultSuccess();
  }
}
