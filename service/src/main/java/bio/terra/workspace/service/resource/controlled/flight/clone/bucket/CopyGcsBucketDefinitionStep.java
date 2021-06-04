package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.common.exception.BadRequestException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class CopyGcsBucketDefinitionStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ControlledResourceService controlledResourceService;
  private final UUID destinationWorkspaceId;
  @Nullable private final String suppliedResourceName;
  @Nullable private final String suppliedDescription;
  @Nullable private final String suppliedBucketName;

  public CopyGcsBucketDefinitionStep(
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ControlledResourceService controlledResourceService,
      UUID destinationWorkspaceId,
      @Nullable String suppliedResourceName,
      @Nullable String suppliedDescription,
      @Nullable String suppliedBucketName) {
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.controlledResourceService = controlledResourceService;
    this.destinationWorkspaceId = destinationWorkspaceId;
    this.suppliedResourceName = suppliedResourceName;
    this.suppliedDescription = suppliedDescription;
    this.suppliedBucketName = suppliedBucketName;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String resourceName =
        Optional.ofNullable(this.suppliedResourceName)
            .orElseGet(
                () -> workingMap.get(ControlledResourceKeys.PREVIOUS_RESOURCE_NAME, String.class));
    final String description =
        Optional.ofNullable(suppliedDescription)
            .orElseGet(
                () ->
                    workingMap.get(
                        ControlledResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class));
    final String bucketName =
        Optional.ofNullable(suppliedBucketName).orElseGet(this::randomBucketName);
    // get creation parameters together from working map and input map
    // build a create flight
    ControlledGcsBucketResource destinationBucket =
        new ControlledGcsBucketResource(
            destinationWorkspaceId,
            UUID.randomUUID(),
            resourceName,
            description,
            sourceBucket.getCloningInstructions(),
            sourceBucket.getAssignedUser().orElse(null),
            sourceBucket.getAccessScope(),
            sourceBucket.getManagedBy(),
            bucketName);
    final ApiGcpGcsBucketCreationParameters creationParameters =
        workingMap.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    final List<ControlledResourceIamRole> iamRoles = getIamRoles(sourceBucket.getAccessScope());
    final ControlledGcsBucketResource clonedBucket =
        controlledResourceService.createBucket(
            destinationBucket, creationParameters, iamRoles, userRequest);
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedBucket);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final ControlledGcsBucketResource clonedBucket =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledGcsBucketResource.class);
    controlledResourceService.deleteControlledResourceSync(
        clonedBucket.getWorkspaceId(), clonedBucket.getResourceId(), userRequest);
    return StepResult.getStepResultSuccess();
  }

  private String randomBucketName() {
    return UUID.randomUUID().toString().toLowerCase();
  }

  /**
   * Build the list of IAM roles for this user. If the resource was initially shared, we make the
   * cloned resource shared as well. If it's private, the user making the clone must be the owner
   * and becomes EDITOR on the new resource.
   *
   * @param accessScope
   * @return
   */
  private List<ControlledResourceIamRole> getIamRoles(AccessScopeType accessScope) {
    switch (accessScope) {
      case ACCESS_SCOPE_SHARED:
        return Collections.emptyList();
      case ACCESS_SCOPE_PRIVATE:
        return Collections.singletonList(ControlledResourceIamRole.EDITOR);
      default:
        throw new BadRequestException(
            String.format("Access Scope %s is not recognized.", accessScope.toString()));
    }
  }
}
