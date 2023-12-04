package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.security.SecureRandom;

public class CreateDbDumpEncryptionKeyStep implements Step {
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {

    int keyLengthBytes = 32; // 256 bits
    int ivLength = 12; // 96 bits

    byte[] keyAndIvBytes = new byte[keyLengthBytes + ivLength];
    new SecureRandom().nextBytes(keyAndIvBytes);
    String encodedString = java.util.Base64.getEncoder().encodeToString(keyAndIvBytes);

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
