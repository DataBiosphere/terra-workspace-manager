package bio.terra.workspace.service.resource.controlled.flight;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRule;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleCondition;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcsBucketResource;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CreateGcsBucketStep implements Step {

  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;
  private final AuthenticatedUserRequest userRequest;

  public CreateGcsBucketStep(CrlService crlService,
      ControlledGcsBucketResource resource,
      AuthenticatedUserRequest userRequest) {
    this.crlService = crlService;
    this.resource = resource;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    final BucketInfo bucketInfo = BucketInfo.newBuilder(resource.getBucketName())
        .setLocation(resource.getLocation())
        .setStorageClass(ApiConversions.toGcsApi(resource.getDefaultStorageClass()))
        .setLifecycleRules(ApiConversions.toGcsApi(resource.getLifecycle()))
        .build();

    final StorageCow storageCow = crlService.createStorageCow(userRequest);
    final BucketCow bucketCow = storageCow.create(bucketInfo);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final StorageCow storageCow = crlService.createStorageCow(userRequest);
    final boolean deleted = storageCow.delete(resource.getBucketName());
    
    return StepResult.getStepResultSuccess();
  }

  private static class ApiConversions {

    private ApiConversions() {
    }

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
      return new LifecycleRule(toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
    }

    private static LifecycleAction toGcsApi(GoogleBucketLifecycleRuleAction lifecycleRuleAction) {
      switch (lifecycleRuleAction.getType()) {
        case DELETE:
          return LifecycleAction.newDeleteAction();
        case SET_STORAGE_CLASS:
          return LifecycleAction.newSetStorageClassAction(toGcsApi(lifecycleRuleAction.getStorageClass()));
        default:
          throw new IllegalStateException("Unrecognized lifecycle action type " + lifecycleRuleAction.getType());
      }
    }

    private static LifecycleCondition toGcsApi(GoogleBucketLifecycleRuleCondition condition) {
      final LifecycleCondition.Builder resultBuilder = LifecycleCondition.newBuilder();

  // TODO: some conditions aren't on the Google api object
      Optional.ofNullable(condition.getAge()).ifPresent(resultBuilder::setAge);
      Optional.ofNullable(condition.getCreatedBefore())
          .ifPresent(t -> resultBuilder.setCreatedBefore(toDateTime(t)));
      Optional.ofNullable(condition.getNumNewerVersions())
          .ifPresent(resultBuilder::setNumberOfNewerVersions);
      Optional.ofNullable(condition.isLive()).ifPresent(resultBuilder::setIsLive);
      final List<StorageClass> storageClasses = condition.getMatchesStorageClass().stream()
          .map(ApiConversions::toGcsApi)
          .collect(Collectors.toList());
      resultBuilder.setMatchesStorageClass(storageClasses);

      return resultBuilder.build();
    }

    private static DateTime toDateTime(LocalDate localDate) {
      return new DateTime(localDate
          .atStartOfDay()
          .atOffset(ZoneOffset.UTC)
          .toInstant()
          .toEpochMilli());
    }
  }

}
