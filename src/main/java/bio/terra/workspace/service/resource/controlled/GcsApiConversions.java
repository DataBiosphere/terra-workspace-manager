package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.StorageClass;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class GcsApiConversions {

  private GcsApiConversions() {}

  public static StorageClass toGcsApi(ApiGcpGcsBucketDefaultStorageClass storageClass) {
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

  public static List<LifecycleRule> toGcsApi(ApiGcpGcsBucketLifecycle lifecycle) {
    return lifecycle.getRules().stream()
        .map(GcsApiConversions::toGcsApi)
        .collect(Collectors.toList());
  }

  public static LifecycleRule toGcsApi(ApiGcpGcsBucketLifecycleRule lifecycleRule) {
    return new LifecycleRule(
        toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
  }

  public static LifecycleAction toGcsApi(ApiGcpGcsBucketLifecycleRuleAction lifecycleRuleAction) {
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

  public static LifecycleCondition toGcsApi(ApiGcpGcsBucketLifecycleRuleCondition condition) {
    final LifecycleCondition.Builder resultBuilder = LifecycleCondition.newBuilder();

    /* TODO(PF-506): some conditions aren't in the version of the Google Storage API in the
     *    latest version of the CRL. */
    resultBuilder.setAge(condition.getAge());
    resultBuilder.setCreatedBefore(toDateTime(condition.getCreatedBefore()));
    resultBuilder.setNumberOfNewerVersions(condition.getNumNewerVersions());
    resultBuilder.setIsLive(condition.isLive());

    resultBuilder.setMatchesStorageClass(
        condition.getMatchesStorageClass().stream()
            .map(GcsApiConversions::toGcsApi)
            .collect(Collectors.toList()));

    return resultBuilder.build();
  }

  @Nullable
  private static DateTime toDateTime(@Nullable OffsetDateTime offsetDateTime) {
    return Optional.ofNullable(offsetDateTime)
        .map(OffsetDateTime::toInstant)
        .map(Instant::toEpochMilli)
        .map(DateTime::new)
        .orElse(null);
  }
}
