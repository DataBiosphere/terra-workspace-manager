package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import com.google.common.annotations.VisibleForTesting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateGcsBucketStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateGcsBucketStep.class);
  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final WorkspaceService workspaceService;

  public CreateGcsBucketStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      WorkspaceService workspaceService) {
    this.crlService = crlService;
    this.resource = resource;
    this.workspaceService = workspaceService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    ApiGcpGcsBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcpGcsBucketCreationParameters.class);
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    BucketInfo.Builder bucketInfoBuilder =
        BucketInfo.newBuilder(resource.getBucketName())
            .setLocation(creationParameters.getLocation());

    // Remaining creation parameters are optional
    Optional.ofNullable(creationParameters.getDefaultStorageClass())
        .map(ApiConversions::toGcsApi)
        .ifPresent(bucketInfoBuilder::setStorageClass);

    bucketInfoBuilder.setLifecycleRules(
        Optional.ofNullable(creationParameters.getLifecycle())
            .map(ApiConversions::toGcsApi)
            .orElse(Collections.emptyList()));

    StorageCow storageCow = crlService.createStorageCow(projectId);

    // Don't try to create it if it already exists. At this point the assumption is
    // this is a redo and this step created it already.
    BucketCow existingBucket = storageCow.get(resource.getBucketName());
    if (existingBucket == null) {
      storageCow.create(bucketInfoBuilder.build());
    } else {
      logger.info("Bucket {} already exists. Continuing.", resource.getBucketName());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    storageCow.delete(resource.getBucketName());
    return StepResult.getStepResultSuccess();
  }

  public static class ApiConversions {

    private ApiConversions() {}

    private static StorageClass toGcsApi(ApiGcpGcsBucketDefaultStorageClass storageClass) {
      switch (storageClass) {
        case STANDARD:
          return StorageClass.STANDARD;
        case NEARLINE:
          return StorageClass.NEARLINE;
        case COLDLINE:
          return StorageClass.COLDLINE;
        case ARCHIVE:
          return StorageClass.ARCHIVE;
        default:
          throw new IllegalStateException("Unrecognized storage class " + storageClass);
      }
    }

    private static List<LifecycleRule> toGcsApi(ApiGcpGcsBucketLifecycle lifecycle) {
      return lifecycle.getRules().stream()
          .map(ApiConversions::toGcsApi)
          .collect(Collectors.toList());
    }

    private static LifecycleRule toGcsApi(ApiGcpGcsBucketLifecycleRule lifecycleRule) {
      return new LifecycleRule(
          toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
    }

    private static LifecycleAction toGcsApi(
        ApiGcpGcsBucketLifecycleRuleAction lifecycleRuleAction) {
      switch (lifecycleRuleAction.getType()) {
        case DELETE:
          return LifecycleAction.newDeleteAction();
        case SET_STORAGE_CLASS:
          return LifecycleAction.newSetStorageClassAction(
              toGcsApi(lifecycleRuleAction.getStorageClass()));
        default:
          throw new IllegalStateException(
              "Unrecognized lifecycle action type " + lifecycleRuleAction.getType());
      }
    }

    private static LifecycleCondition toGcsApi(ApiGcpGcsBucketLifecycleRuleCondition condition) {
      final LifecycleCondition.Builder resultBuilder = LifecycleCondition.newBuilder();

      /* TODO(PF-506): some conditions aren't in the version of the Google Storage API in the
       *    latest version of the CRL. */
      resultBuilder.setAge(condition.getAge());
      resultBuilder.setCreatedBefore(toDateTime(condition.getCreatedBefore()));
      resultBuilder.setNumberOfNewerVersions(condition.getNumNewerVersions());
      resultBuilder.setIsLive(condition.isLive());

      resultBuilder.setMatchesStorageClass(
          condition.getMatchesStorageClass().stream()
              .map(ApiConversions::toGcsApi)
              .collect(Collectors.toList()));

      return resultBuilder.build();
    }

    @VisibleForTesting
    @Nullable
    public static DateTime toDateTime(@Nullable OffsetDateTime offsetDateTime) {
      return Optional.ofNullable(offsetDateTime)
          .map(OffsetDateTime::toInstant)
          .map(Instant::toEpochMilli)
          .map(DateTime::new)
          .orElse(null);
    }
  }
}
