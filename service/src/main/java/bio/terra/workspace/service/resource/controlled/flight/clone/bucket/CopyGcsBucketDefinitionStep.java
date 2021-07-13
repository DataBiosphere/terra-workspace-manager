package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.EnumNotRecognizedException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CopyGcsBucketDefinitionStep implements Step {

  private final AuthenticatedUserRequest userRequest;
  private final ControlledGcsBucketResource sourceBucket;
  private final ControlledResourceService controlledResourceService;

  public CopyGcsBucketDefinitionStep(
      AuthenticatedUserRequest userRequest,
      ControlledGcsBucketResource sourceBucket,
      ControlledResourceService controlledResourceService) {
    this.userRequest = userRequest;
    this.sourceBucket = sourceBucket;
    this.controlledResourceService = controlledResourceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final FlightMap workingMap = flightContext.getWorkingMap();
    final CloningInstructions cloningInstructions =
        Optional.ofNullable(
                inputParameters.get(
                    ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class))
            .orElse(sourceBucket.getCloningInstructions());
    // future steps need the resolved cloning instructions
    workingMap.put(ControlledResourceKeys.CLONING_INSTRUCTIONS, cloningInstructions);
    if (CloningInstructions.COPY_NOTHING.equals(cloningInstructions)) {
      final ApiClonedControlledGcpGcsBucket noOpResult =
          new ApiClonedControlledGcpGcsBucket()
              .effectiveCloningInstructions(cloningInstructions.toApiModel())
              .bucket(null)
              .sourceWorkspaceId(sourceBucket.getWorkspaceId())
              .sourceResourceId(sourceBucket.getResourceId());
      FlightUtils.setResponse(flightContext, noOpResult, HttpStatus.OK);
      return StepResult.getStepResultSuccess();
    } // todo: handle COPY_REFERENCE PF-811, PF-812
    final String resourceName =
        getSuppliedOrPreviousValue(
            flightContext,
            ControlledResourceKeys.RESOURCE_NAME,
            ControlledResourceKeys.PREVIOUS_RESOURCE_NAME,
            String.class);
    final String description =
        getSuppliedOrPreviousValue(
            flightContext,
            ControlledResourceKeys.RESOURCE_DESCRIPTION,
            ControlledResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION,
            String.class);
    final String bucketName =
        Optional.ofNullable(
                inputParameters.get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class))
            .orElseGet(this::randomBucketName);
    // Store effective bucket name for destination
    workingMap.put(ControlledResourceKeys.DESTINATION_BUCKET_NAME, bucketName);
    final UUID destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);

    // bucket resource for create flight
    ControlledGcsBucketResource destinationBucketResource =
        new ControlledGcsBucketResource(
            destinationWorkspaceId,
            UUID.randomUUID(), // random ID for new resource
            resourceName,
            description,
            sourceBucket.getCloningInstructions(),
            sourceBucket.getAssignedUser().orElse(null),
            sourceBucket.getAccessScope(),
            sourceBucket.getManagedBy(),
            bucketName);

    final ApiGcpGcsBucketCreationParameters destinationCreationParameters =
        getDestinationCreationParameters(inputParameters, workingMap);

    final List<ControlledResourceIamRole> iamRoles = getIamRoles(sourceBucket.getAccessScope());

    // Launch a CreateControlledResourcesFlight to make the destination bucket
    final ControlledGcsBucketResource clonedBucket =
        controlledResourceService.createBucket(
            destinationBucketResource, destinationCreationParameters, iamRoles, userRequest);
    workingMap.put(ControlledResourceKeys.CLONED_RESOURCE_DEFINITION, clonedBucket);
    // TODO: create new type & use it here
    final ApiCreatedControlledGcpGcsBucket apiCreatedBucket =
        new ApiCreatedControlledGcpGcsBucket()
            .gcpBucket(clonedBucket.toApiResource())
            .resourceId(destinationBucketResource.getResourceId());
    // todo: bundle everything so it doesn't use API types here.
    final ApiClonedControlledGcpGcsBucket apiBucketResult =
        new ApiClonedControlledGcpGcsBucket()
            .effectiveCloningInstructions(cloningInstructions.toApiModel())
            .bucket(apiCreatedBucket)
            .sourceWorkspaceId(sourceBucket.getWorkspaceId())
            .sourceResourceId(sourceBucket.getResourceId());
    workingMap.put(ControlledResourceKeys.CLONE_DEFINITION_RESULT, apiBucketResult);
    if (cloningInstructions.equals(CloningInstructions.COPY_DEFINITION)) {
      FlightUtils.setResponse(flightContext, apiBucketResult, HttpStatus.OK);
    }
    return StepResult.getStepResultSuccess();
  }

  // Delete the bucket and its row in the resource table
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final ControlledGcsBucketResource clonedBucket =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
                ControlledGcsBucketResource.class);
    if (clonedBucket != null) {
      controlledResourceService.deleteControlledResourceSync(
          clonedBucket.getWorkspaceId(), clonedBucket.getResourceId(), userRequest);
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Get a supplied input value from input parameters, or, if that's missing, a default (previous)
   * value from the working map, or null.
   *
   * @param flightContext - context object for the flight, used to get the input & working maps
   * @param suppliedKey - key in input parameters for the supplied (override) value
   * @param previousKey - key in the working map for the previous value
   * @param klass - class of the value, e.g. String.class
   * @param <T> - type parameter corresponding to the klass
   * @return - a value from one of the two sources, or null
   */
  private <T> T getSuppliedOrPreviousValue(
      FlightContext flightContext, String suppliedKey, String previousKey, Class<T> klass) {
    return Optional.ofNullable(flightContext.getInputParameters().get(suppliedKey, klass))
        .orElse(flightContext.getWorkingMap().get(previousKey, klass));
  }

  private ApiGcpGcsBucketCreationParameters getDestinationCreationParameters(
      FlightMap inputParameters, FlightMap workingMap) {
    final ApiGcpGcsBucketCreationParameters sourceCreationParameters =
        workingMap.get(
            ControlledResourceKeys.CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    final Optional<String> suppliedLocation =
        Optional.ofNullable(inputParameters.get(ControlledResourceKeys.LOCATION, String.class));

    // Override the location parameter if it was specified
    if (suppliedLocation.isPresent()) {
      return new ApiGcpGcsBucketCreationParameters()
          .defaultStorageClass(sourceCreationParameters.getDefaultStorageClass())
          .lifecycle(sourceCreationParameters.getLifecycle())
          .name(sourceCreationParameters.getName())
          .location(suppliedLocation.get());
    } else {
      return sourceCreationParameters;
    }
  }

  private String randomBucketName() {
    return "terra-wsm-" + UUID.randomUUID().toString().toLowerCase();
  }

  /**
   * Build the list of IAM roles for this user. If the resource was initially shared, we make the
   * cloned resource shared as well. If it's private, the user making the clone must be the resource
   * user and becomes EDITOR, READER, and WRITER on the new resource.
   *
   * @param accessScope - private vs shared access
   * @return list of IAM roles for the user on the resource
   */
  private List<ControlledResourceIamRole> getIamRoles(AccessScopeType accessScope) {
    switch (accessScope) {
      case ACCESS_SCOPE_SHARED:
        return Collections.emptyList();
      case ACCESS_SCOPE_PRIVATE:
        // User owns the cloned private resource completely
        return List.of(
            ControlledResourceIamRole.READER,
            ControlledResourceIamRole.WRITER,
            ControlledResourceIamRole.EDITOR);
      default:
        throw new EnumNotRecognizedException(
            String.format("Access Scope %s is not recognized.", accessScope.toString()));
    }
  }
}
