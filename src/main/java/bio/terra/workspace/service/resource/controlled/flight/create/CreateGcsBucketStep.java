package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcsBucketCreationParameters;
import bio.terra.workspace.generated.model.ApiGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class CreateGcsBucketStep implements Step {

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
    ApiGcsBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, ApiGcsBucketCreationParameters.class);
    String projectId = workspaceService.getRequiredGcpProject(resource.getWorkspaceId());
    BucketInfo bucketInfo =
        BucketInfo.newBuilder(resource.getBucketName())
            .setLocation(creationParameters.getLocation())
            .setStorageClass(ApiConversions.toGcsApi(creationParameters.getDefaultStorageClass()))
            .setLifecycleRules(ApiConversions.toGcsApi(creationParameters.getLifecycle()))
            .build();

    StorageCow storageCow = crlService.createStorageCow(projectId);
    storageCow.create(bucketInfo);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String projectId = workspaceService.getRequiredGcpProject(workspaceId);
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    storageCow.delete(resource.getBucketName());
    return StepResult.getStepResultSuccess();
  }

  private static class ApiConversions {

    private ApiConversions() {}

    private static StorageClass toGcsApi(ApiGcsBucketDefaultStorageClass storageClass) {
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

    private static List<LifecycleRule> toGcsApi(ApiGcsBucketLifecycle lifecycle) {
      return lifecycle.getRules().stream()
          .map(ApiConversions::toGcsApi)
          .collect(Collectors.toList());
    }

    private static LifecycleRule toGcsApi(ApiGcsBucketLifecycleRule lifecycleRule) {
      return new LifecycleRule(
          toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
    }

    private static LifecycleAction toGcsApi(ApiGcsBucketLifecycleRuleAction lifecycleRuleAction) {
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

    private static LifecycleCondition toGcsApi(ApiGcsBucketLifecycleRuleCondition condition) {
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

    private static DateTime toDateTime(@Nullable LocalDate localDate) {
      return Optional.ofNullable(localDate)
          .map(LocalDate::atStartOfDay)
          .map(ldt -> ldt.atOffset(ZoneOffset.UTC))
          .map(OffsetDateTime::toInstant)
          .map(Instant::toEpochMilli)
          .map(DateTime::new)
          .orElse(null);
    }
  }
}
