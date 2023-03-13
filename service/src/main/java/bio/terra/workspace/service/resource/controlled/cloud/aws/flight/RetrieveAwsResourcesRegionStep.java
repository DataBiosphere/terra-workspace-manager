package bio.terra.workspace.service.resource.controlled.cloud.aws.flight;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket.ControlledAwsS3BucketResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import java.util.*;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Retrieves the Aws resource's cloud region according to the resource type. */
public class RetrieveAwsResourcesRegionStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(RetrieveAwsResourcesRegionStep.class);

  private final AwsConfiguration awsConfiguration;

  public RetrieveAwsResourcesRegionStep(AwsConfiguration awsConfiguration) {
    this.awsConfiguration = awsConfiguration;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getWorkingMap(),
        CONTROLLED_RESOURCES_WITHOUT_REGION,
        WORKSPACE_ID_TO_AWS_CLOUD_CONTEXT_MAP);
    List<ControlledResource> controlledResources =
        context.getWorkingMap().get(CONTROLLED_RESOURCES_WITHOUT_REGION, new TypeReference<>() {});
    Map<UUID, String> workspaceIdToAwsCloudContextMap =
        context
            .getWorkingMap()
            .get(WORKSPACE_ID_TO_AWS_CLOUD_CONTEXT_MAP, new TypeReference<>() {});
    Map<UUID, String> resourceIdToRegionMap = new HashMap<>();
    Map<UUID, String> resourceIdToWorkspaceIdMap = new HashMap<>();
    for (var resource : Objects.requireNonNull(controlledResources)) {
      WsmResourceType resourceType = resource.getResourceType();
      logger.info(
          "Getting cloud region for resource {} in workspace {}",
          resource.getResourceId(),
          resource.getWorkspaceId());
      Preconditions.checkState(
          Objects.requireNonNull(workspaceIdToAwsCloudContextMap)
              .containsKey(resource.getWorkspaceId()),
          "Aws workspace %s must have an aws cloud context",
          resource.getWorkspaceId());
      AwsCloudContext awsCloudContext =
          AwsCloudContext.deserialize(
              workspaceIdToAwsCloudContextMap.get(resource.getWorkspaceId()));

      switch (resourceType) {
        case CONTROLLED_AWS_BUCKET, CONTROLLED_AWS_SAGEMAKER_NOTEBOOK -> populateMapsWithResourceIdKey(
            resourceIdToRegionMap,
            resourceIdToWorkspaceIdMap,
            resource,
            getAwsRegion(resource.castByEnum(resourceType), awsCloudContext, awsConfiguration));
        default -> throw new UnsupportedOperationException(
            String.format(
                "resource of type %s is not an aws resource or is a referenced resource",
                resourceType));
      }
    }
    context.getWorkingMap().put(CONTROLLED_RESOURCE_ID_TO_REGION_MAP, resourceIdToRegionMap);
    context
        .getWorkingMap()
        .put(CONTROLLED_RESOURCE_ID_TO_WORKSPACE_ID_MAP, resourceIdToWorkspaceIdMap);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // READ-ONLY step, do nothing here.
    return StepResult.getStepResultSuccess();
  }

  private void populateMapsWithResourceIdKey(
      Map<UUID, String> resourceIdToRegionMap,
      Map<UUID, String> resourceIdToWorkspaceIdMap,
      ControlledResource resource,
      @Nullable String region) {
    if (region != null) {
      UUID resourceId = resource.getResourceId();
      resourceIdToRegionMap.put(resourceId, region);
      resourceIdToWorkspaceIdMap.put(resourceId, resource.getWorkspaceId().toString());
    }
  }

  private String getAwsRegion(
      ControlledAwsS3BucketResource resource,
      AwsCloudContext awsCloudContext,
      AwsConfiguration awsConfiguration) {
    String region = resource.getRegion();
    if (region != null) {
      return region;
    }

    AwsConfiguration.AwsLandingZoneConfiguration landingZoneConfiguration =
        awsConfiguration.getLandingZoneByName(awsCloudContext.getLandingZoneName());
    if (landingZoneConfiguration != null) {
      region =
          landingZoneConfiguration.getBuckets().stream()
              .findFirst()
              .map(AwsConfiguration.AwsLandingZoneBucket::getRegion)
              .orElse(null);
    }

    return region != null ? region : "us-east-1"; // default region
  }
}
