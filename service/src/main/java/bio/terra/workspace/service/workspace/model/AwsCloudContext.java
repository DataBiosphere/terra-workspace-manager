package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.AwsLandingZoneBucket;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.AwsLandingZoneConfiguration;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nullable;

public class AwsCloudContext {
  private static final Logger logger = LoggerFactory.getLogger(AwsCloudContext.class);

  private String landingZoneName;
  private String accountNumber;
  private String serviceRoleArn;
  private String serviceRoleAudience;
  private String userRoleArn;
  private String kmsKeyArn;
  @Nullable private String notebookLifecycleConfigArn;
  private Map<String, String> regionToBucketNameMap;

  // Constructor for Jackson
  public AwsCloudContext() {}

  // Constructor for deserializer
  public AwsCloudContext(
          String landingZoneName,
          String accountNumber,
          String serviceRoleArn,
          String serviceRoleAudience,
          String userRoleArn,
          String kmsKeyArn,
          @Nullable String notebookLifecycleConfigArn,
          Map<String, String> regionToBucketNameMap) {
    this.landingZoneName = landingZoneName;
    this.accountNumber = accountNumber;
    this.serviceRoleArn = serviceRoleArn;
    this.serviceRoleAudience = serviceRoleAudience;
    this.userRoleArn = userRoleArn;
    this.kmsKeyArn = kmsKeyArn;
    this.notebookLifecycleConfigArn = notebookLifecycleConfigArn;
    this.regionToBucketNameMap = regionToBucketNameMap;
  }

  public String getLandingZoneName() {
    return landingZoneName;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getServiceRoleArn() {
    return serviceRoleArn;
  }

  public String getServiceRoleAudience() {
    return serviceRoleAudience;
  }

  public String getUserRoleArn() {
    return userRoleArn;
  }

  public String getKmsKeyArn() {
    return kmsKeyArn;
  }

  public @Nullable String getNotebookLifecycleConfigArn() {
    return notebookLifecycleConfigArn;
  }

  public Map<String, String> getRegionToBucketNameMap() {
    return regionToBucketNameMap;
  }

  @JsonIgnore
  public String getBucketNameForRegion(Region region) {
    return regionToBucketNameMap.get(region.toString());
  }

  public static AwsCloudContext fromConfiguration(
      AwsLandingZoneConfiguration landingZoneConfiguration, String serviceRoleAudience) {
    Map<String, String> bucketMap =
        landingZoneConfiguration.getBuckets().stream()
            .collect(
                Collectors.toMap(AwsLandingZoneBucket::getRegion, AwsLandingZoneBucket::getName));

    return new AwsCloudContext(
        landingZoneConfiguration.getName(),
        landingZoneConfiguration.getAccountNumber(),
        landingZoneConfiguration.getServiceRoleArn(),
        serviceRoleAudience,
        landingZoneConfiguration.getUserRoleArn(),
        landingZoneConfiguration.getKmsKeyArn(),
        landingZoneConfiguration.getNotebookLifecycleConfigArn(),
        bucketMap);
  }

  public ApiAwsContext toApi() {
    return new ApiAwsContext().landingZoneId(landingZoneName).accountNumber(accountNumber);
  }

  public String serialize() {
    AwsCloudContextV1 dbContext = new AwsCloudContextV1(this);
    return DbSerDes.toJson(dbContext);
  }

  public static @Nullable AwsCloudContext deserialize(@Nullable String json) {
    if (json == null) {
      return null;
    }

    try {
      AwsCloudContextV1 dbContext = DbSerDes.fromJson(json, AwsCloudContextV1.class);
      dbContext.validateVersion();

      List<AwsCloudContextBucketV1> bucketList =
          DbSerDes.fromJson(dbContext.bucketList, new TypeReference<>() {});
      Map<String, String> bucketMap = new HashMap<>();
      bucketList.forEach(
          bucketV1 -> {
            bucketV1.validateVersion();
            bucketMap.put(bucketV1.regionName, bucketV1.bucketName);
          });

      return new AwsCloudContext(
          dbContext.landingZoneName,
          dbContext.accountNumber,
          dbContext.serviceRoleArn,
          dbContext.serviceRoleAudience,
          dbContext.userRoleArn,
          dbContext.kmsKeyArn,
          dbContext.notebookLifecycleConfigArn,
          bucketMap);

    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version: " + e);
    }
  }

  /** Mask AWS version numbers so as not to collide with Azure and GCP version numbers */
  public static final long AWS_CLOUD_CONTEXT_DB_VERSION_MASK = 0x100;

  public static class AwsCloudContextBucketV1 {
    public static final long AWS_CLOUD_CONTEXT_BUCKET_DB_VERSION = 1;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String regionName;
    public String bucketName;

    @JsonCreator
    public AwsCloudContextBucketV1(
        @JsonProperty("version") long version,
        @JsonProperty("regionName") String regionName,
        @JsonProperty("bucketName") String bucketName) {
      this.version = version;
      this.regionName = regionName;
      this.bucketName = bucketName;
    }

    public AwsCloudContextBucketV1(String regionName, String bucketName) {
      version = AWS_CLOUD_CONTEXT_BUCKET_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
      this.regionName = regionName;
      this.bucketName = bucketName;
    }

    public void validateVersion() {
      if (this.version
          != (AWS_CLOUD_CONTEXT_BUCKET_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK)) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextBucketV1");
      }
    }
  }

  public static class AwsCloudContextV1 {
    public static final long AWS_CLOUD_CONTEXT_DB_VERSION = 1;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String landingZoneName;
    public String accountNumber;
    public String serviceRoleArn;
    public String serviceRoleAudience;
    public String userRoleArn;
    public String kmsKeyArn;
    public String notebookLifecycleConfigArn;
    public String bucketList;

    @JsonCreator
    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("landingZoneName") String landingZoneName,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("serviceRoleArn") String serviceRoleArn,
        @JsonProperty("serviceRoleAudience") String serviceRoleAudience,
        @JsonProperty("userRoleArn") String userRoleArn,
        @JsonProperty("kmsKeyArn") String kmsKeyArn,
        @JsonProperty("notebookLifecycleConfigArn") String notebookLifecycleConfigArn,
        @JsonProperty("bucketList") String bucketList) {
      this.version = version;
      this.landingZoneName = landingZoneName;
      this.accountNumber = accountNumber;
      this.serviceRoleArn = serviceRoleArn;
      this.serviceRoleAudience = serviceRoleAudience;
      this.userRoleArn = userRoleArn;
      this.kmsKeyArn = kmsKeyArn;
      this.notebookLifecycleConfigArn = notebookLifecycleConfigArn;
      this.bucketList = bucketList;
    }

    public AwsCloudContextV1(AwsCloudContext context) {
      this.version = AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
      this.landingZoneName = context.landingZoneName;
      this.accountNumber = context.accountNumber;
      this.serviceRoleArn = context.serviceRoleArn;
      this.serviceRoleAudience = context.serviceRoleAudience;
      this.userRoleArn = context.userRoleArn;
      this.kmsKeyArn = context.kmsKeyArn;

      if (context.notebookLifecycleConfigArn != null) { // optional and may be null
        this.notebookLifecycleConfigArn = context.notebookLifecycleConfigArn;
      }

      List<AwsCloudContextBucketV1> bucketListInternal =
          context.regionToBucketNameMap.entrySet().stream()
              .map(bucket -> new AwsCloudContextBucketV1(bucket.getKey(), bucket.getValue()))
              .collect(Collectors.toList());
      this.bucketList = DbSerDes.toJson(bucketListInternal);
    }

    public void validateVersion() {
      if (this.version != (AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK)) {
        throw new InvalidSerializedVersionException(
            "Invalid serialized version of AwsCloudContextV1");
      }
    }
  }
}
