package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.generated.model.ApiGcpGcsBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycle;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRule;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketLifecycleRuleCondition;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import com.google.api.client.util.DateTime;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.BucketInfo.LifecycleRule.DeleteLifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleAction;
import com.google.cloud.storage.BucketInfo.LifecycleRule.LifecycleCondition;
import com.google.cloud.storage.BucketInfo.LifecycleRule.SetStorageClassLifecycleAction;
import com.google.cloud.storage.StorageClass;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class GcsApiConversions {

  private GcsApiConversions() {}

  private static final BiMap<ApiGcpGcsBucketDefaultStorageClass, StorageClass> STORAGE_CLASS_MAP =
      HashBiMap.create();

  static {
    STORAGE_CLASS_MAP.put(ApiGcpGcsBucketDefaultStorageClass.STANDARD, StorageClass.STANDARD);
    STORAGE_CLASS_MAP.put(ApiGcpGcsBucketDefaultStorageClass.NEARLINE, StorageClass.NEARLINE);
    STORAGE_CLASS_MAP.put(ApiGcpGcsBucketDefaultStorageClass.COLDLINE, StorageClass.COLDLINE);
    STORAGE_CLASS_MAP.put(ApiGcpGcsBucketDefaultStorageClass.ARCHIVE, StorageClass.ARCHIVE);
  }

  /**
   * Convert to update parameters, which are a subset of creation parameters
   *
   * @param bucketInfo - GCS API-proivded BucketInfo instance
   * @return - populated parameters object
   */
  public static ApiGcpGcsBucketUpdateParameters toUpdateParameters(BucketInfo bucketInfo) {
    return new ApiGcpGcsBucketUpdateParameters()
        .lifecycle(new ApiGcpGcsBucketLifecycle().rules(toWsmApiRulesList(bucketInfo)))
        .defaultStorageClass(toWsmApi(bucketInfo.getStorageClass()));
  }

  /**
   * build an incomplete BucketInfo object corresponding to the fields to be updated TODO: add
   * conversion to/from creation parameters as well
   *
   * @param bucketName - existing name for the bucket
   * @param updateParameters - update structure. Null field means no change.
   * @return
   */
  public static BucketInfo toBucketInfo(
      String bucketName, ApiGcpGcsBucketUpdateParameters updateParameters) {
    return BucketInfo.newBuilder(bucketName)
        .setStorageClass(toGcsApi(updateParameters.getDefaultStorageClass()))
        .setLifecycleRules(toGcsApiRulesList(updateParameters.getLifecycle()))
        .build();
  }

  // If input is null, return null, as we need the null value preserved in case it's not there.
  // This prevents a null check at the call site at the expense of an extra clause here.
  // Returns either null, if no input, the correct StorageClass, if found, or throws
  // IllegalStateException if the storage class given isn't in the map.
  @Nullable
  public static StorageClass toGcsApi(@Nullable ApiGcpGcsBucketDefaultStorageClass storageClass) {
    if (storageClass == null) {
      return null;
    }
    return Optional.ofNullable(STORAGE_CLASS_MAP.get(storageClass))
        .orElseThrow(() -> new IllegalStateException("Unrecognized storage class " + storageClass));
  }

  @Nullable
  public static ApiGcpGcsBucketDefaultStorageClass toWsmApi(@Nullable StorageClass storageClass) {
    if (storageClass == null) {
      return null;
    }
    return Optional.ofNullable(STORAGE_CLASS_MAP.inverse().get(storageClass))
        .orElseThrow(() -> new IllegalStateException("Unrecognized storage class " + storageClass));
  }

  // Deliberately return a null collection, because we're working with an API convention
  // where null means "no change", but an empty list would mean "wipe out this list".
  @Nullable
  public static List<LifecycleRule> toGcsApiRulesList(
      @Nullable ApiGcpGcsBucketLifecycle lifecycle) {
    if (lifecycle == null) {
      return null;
    }
    return lifecycle.getRules().stream()
        .map(GcsApiConversions::toGcsApi)
        .collect(Collectors.toList());
  }

  public static List<ApiGcpGcsBucketLifecycleRule> toWsmApiRulesList(BucketInfo bucketInfo) {
    return bucketInfo.getLifecycleRules().stream()
        .map(GcsApiConversions::toWsmApi)
        .collect(Collectors.toList());
  }

  public static LifecycleRule toGcsApi(ApiGcpGcsBucketLifecycleRule lifecycleRule) {
    return new LifecycleRule(
        toGcsApi(lifecycleRule.getAction()), toGcsApi(lifecycleRule.getCondition()));
  }

  public static ApiGcpGcsBucketLifecycleRule toWsmApi(LifecycleRule lifeCycleRule) {
    return new ApiGcpGcsBucketLifecycleRule()
        .action(toWsmApi(lifeCycleRule.getAction()))
        .condition(toWsmApi(lifeCycleRule.getCondition()));
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

  public static ApiGcpGcsBucketLifecycleRuleAction toWsmApi(LifecycleAction action) {
    final ApiGcpGcsBucketLifecycleRuleActionType actionType = toWsmApi(action.getActionType());
    ApiGcpGcsBucketDefaultStorageClass storageClass =
        getStorageClass(action).map(GcsApiConversions::toWsmApi).orElse(null);
    return new ApiGcpGcsBucketLifecycleRuleAction().storageClass(storageClass).type(actionType);
  }

  public static ApiGcpGcsBucketLifecycleRuleActionType toWsmApi(String lifecycleActionType) {
    switch (lifecycleActionType) {
      case DeleteLifecycleAction.TYPE:
        return ApiGcpGcsBucketLifecycleRuleActionType.DELETE;
      case SetStorageClassLifecycleAction.TYPE:
        return ApiGcpGcsBucketLifecycleRuleActionType.SET_STORAGE_CLASS;
      default:
        throw new IllegalArgumentException(
            String.format(
                "GCS BucketLifecycle action type %s not recognized.", lifecycleActionType));
    }
  }

  /**
   * A subset of lifecycle actions have a storage class associated with them. Currently only the
   * SetStorageClassLifecycleAction. This helper method extracts the storage class if it's present.
   *
   * @param lifecycleAction - A bucket lifecycle action, which may or may not refer to a storage
   *     class.
   * @return - storage class for the action, if it exists.
   */
  public static Optional<StorageClass> getStorageClass(LifecycleAction lifecycleAction) {
    final String actionType = lifecycleAction.getActionType();
    switch (actionType) {
      case DeleteLifecycleAction.TYPE:
        return Optional.empty(); // Delete action has no storage class
      case SetStorageClassLifecycleAction.TYPE:
        if (lifecycleAction instanceof SetStorageClassLifecycleAction) {
          SetStorageClassLifecycleAction storageClassLifecycleAction =
              (SetStorageClassLifecycleAction) lifecycleAction;
          return Optional.of(storageClassLifecycleAction.getStorageClass());
        } else {
          throw new IllegalArgumentException(
              String.format(
                  "Could not cast GCS Bucket LifecycleAction of type %s to SetStorageClassLifecycleAction.",
                  actionType));
        }
      default:
        throw new IllegalArgumentException(
            String.format("GCS BucketLifecycle action type %s not recognized.", actionType));
    }
  }

  public static LifecycleCondition toGcsApi(ApiGcpGcsBucketLifecycleRuleCondition condition) {
    final LifecycleCondition.Builder resultBuilder = LifecycleCondition.newBuilder();

    /* TODO(PF-506): some conditions aren't in the version of the Google Storage API in the
     *    latest version of the CRL. */
    resultBuilder.setAge(condition.getAge());
    resultBuilder.setCreatedBefore(toGoogleDateTime(condition.getCreatedBefore()));
    resultBuilder.setNumberOfNewerVersions(condition.getNumNewerVersions());
    resultBuilder.setIsLive(condition.isLive());

    resultBuilder.setMatchesStorageClass(
        Optional.ofNullable(condition.getMatchesStorageClass())
            .map(sc -> sc.stream().map(GcsApiConversions::toGcsApi).collect(Collectors.toList()))
            .orElse(null)); // need to keep null for update semantics
    return resultBuilder.build();
  }

  public static ApiGcpGcsBucketLifecycleRuleCondition toWsmApi(LifecycleCondition condition) {
    return new ApiGcpGcsBucketLifecycleRuleCondition()
        .age(condition.getAge())
        .createdBefore(toOffsetDateTime(condition.getCreatedBefore()))
        .numNewerVersions(condition.getNumberOfNewerVersions())
        .live(condition.getIsLive())
        .matchesStorageClass(
            Optional.ofNullable(condition.getMatchesStorageClass())
                .map(c -> c.stream().map(GcsApiConversions::toWsmApi).collect(Collectors.toList()))
                .orElse(null));
  }

  @Nullable
  public static DateTime toGoogleDateTime(@Nullable OffsetDateTime offsetDateTime) {
    if (offsetDateTime == null) {
      return null;
    }
    return new DateTime(
        offsetDateTime.toInstant().toEpochMilli(),
        Math.toIntExact(
            Duration.ofSeconds(offsetDateTime.getOffset().getTotalSeconds()).toMinutes()));
  }

  @Nullable
  public static OffsetDateTime toOffsetDateTime(@Nullable DateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(dateTime.getValue()),
        ZoneOffset.ofTotalSeconds(
            Math.toIntExact(Duration.ofMinutes(dateTime.getTimeZoneShift()).toSeconds())));
  }
}
