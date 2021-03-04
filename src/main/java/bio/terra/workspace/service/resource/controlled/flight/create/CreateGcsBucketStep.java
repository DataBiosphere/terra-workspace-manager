package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRule;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleCondition;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
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
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class CreateGcsBucketStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final AuthenticatedUserRequest userRequest;

  public CreateGcsBucketStep(
      CrlService crlService,
      ControlledGcsBucketResource resource,
      AuthenticatedUserRequest userRequest) {
    this.crlService = crlService;
    this.resource = resource;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    GoogleBucketCreationParameters creationParameters =
        inputMap.get(CREATION_PARAMETERS, GoogleBucketCreationParameters.class);

    final BucketInfo bucketInfo =
        BucketInfo.newBuilder(resource.getAttributes().getBucketName())
            .setLocation(creationParameters.getLocation())
            .setStorageClass(ApiConversions.toGcsApi(creationParameters.getDefaultStorageClass()))
            .setLifecycleRules(ApiConversions.toGcsApi(creationParameters.getLifecycle()))
            .build();

    final StorageCow storageCow = crlService.createStorageCow(userRequest);
    final BucketCow bucketCow = storageCow.create(bucketInfo);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final StorageCow storageCow = crlService.createStorageCow(userRequest);
    final boolean deleted = storageCow.delete(resource.getAttributes().getBucketName());
    return StepResult.getStepResultSuccess();
  }

  private static class ApiConversions {

    private ApiConversions() {}

    private static StorageClass toGcsApi(GoogleBucketDefaultStorageClass storageClass) {
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

    private static List<LifecycleRule> toGcsApi(GoogleBucketLifecycle lifecycle) {
      return lifecycle.getRules().stream()
          .map(ApiConversions::toGcsApi)
          .collect(Collectors.toList());
    }

    private static LifecycleRule toGcsApi(GoogleBucketLifecycleRule lifecycleRule) {
      return new LifecycleRule(
          toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
    }

    private static LifecycleAction toGcsApi(GoogleBucketLifecycleRuleAction lifecycleRuleAction) {
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

    private static LifecycleCondition toGcsApi(GoogleBucketLifecycleRuleCondition condition) {
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
