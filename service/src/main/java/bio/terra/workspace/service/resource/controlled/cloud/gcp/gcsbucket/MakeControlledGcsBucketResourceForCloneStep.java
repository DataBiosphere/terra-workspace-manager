package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.flight.newclone.workspace.ControlledGcsBucketParameters;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Make the destination controlled GCS bucket resource object and the associated resource parameters
 * and store them in the working map.
 */
public class MakeControlledGcsBucketResourceForCloneStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(MakeControlledGcsBucketResourceForCloneStep.class);
  private final ControlledResourceFields destinationFields;
  private final ControlledGcsBucketParameters destinationParameters;

  public MakeControlledGcsBucketResourceForCloneStep(
      ControlledResourceFields destinationFields,
      ControlledGcsBucketParameters destinationParameters) {
    this.destinationFields = destinationFields;
    this.destinationParameters = destinationParameters;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    // Merge source and destination parameters to get the proper destination settings
    // The destination bucket name must be filled in in the destination parameters.
    // NOTE: May want to rethink the step split if the update case merges from incoming overrides.
    var sourceParameters =
        workingMap.get(
            ControlledResourceKeys.RESOURCE_PARAMETERS, ControlledGcsBucketParameters.class);
    Objects.requireNonNull(destinationParameters.getBucketName());
    destinationParameters.setStorageClass(sourceParameters.getStorageClass());
    destinationParameters.setLifecycleRules(sourceParameters.getLifecycleRules());
    destinationParameters.setLocation(
        Optional.ofNullable(destinationParameters.getLocation())
            .orElse(sourceParameters.getLocation()));
    workingMap.put(ControlledResourceKeys.RESOURCE_PARAMETERS, destinationParameters);

    // Construct the destination resource
    ControlledGcsBucketResource destinationResource =
        ControlledGcsBucketResource.builder()
            .bucketName(destinationParameters.getBucketName())
            .common(destinationFields)
            .build();
    workingMap.put(ControlledResourceKeys.DESTINATION_RESOURCE, destinationResource);

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
