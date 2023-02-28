package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.AwsLandingZoneBucket;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.AwsLandingZoneConfiguration;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.regions.Region;

public class AwsCloudContext {
  private String landingZoneName;
  private String accountNumber;
  private Arn serviceRoleArn;
  private String serviceRoleAudience;
  private Arn userRoleArn;
  private Arn kmsKeyArn;
  private Arn notebookLifecycleConfigArn;
  private Map<Region, String> regionToBucketNameMap;

  // Constructor for Jackson
  public AwsCloudContext() {}

  // Constructor for deserializer
  public AwsCloudContext(
      String landingZoneName,
      String accountNumber,
      Arn serviceRoleArn,
      String serviceRoleAudience,
      Arn userRoleArn,
      Arn kmsKeyArn,
      Arn notebookLifecycleConfigArn,
      Map<Region, String> regionToBucketNameMap) {
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

  public Arn getServiceRoleArn() {
    return serviceRoleArn;
  }

  public String getServiceRoleAudience() {
    return serviceRoleAudience;
  }

  public Arn getUserRoleArn() {
    return userRoleArn;
  }

  public Arn getKmsKeyArn() {
    return kmsKeyArn;
  }

  public Arn getNotebookLifecycleConfigArn() {
    return notebookLifecycleConfigArn;
  }

  public @Nullable String getBucketNameForRegion(Region region) {
    return regionToBucketNameMap.get(region);
  }

  public static AwsCloudContext fromConfiguration(
      AwsLandingZoneConfiguration landingZoneConfiguration, String serviceRoleAudience) {
    // Serialized context may not have a notebook lifecycle defined, so check for null before
    // attempting to construct an ARN.
    Arn notebookLifecycleConfigArn =
        (landingZoneConfiguration.getNotebookLifecycleConfigArn() != null)
            ? Arn.fromString(landingZoneConfiguration.getNotebookLifecycleConfigArn())
            : null;

    Map<Region, String> bucketMap = new HashMap<>();
    for (AwsLandingZoneBucket bucket : landingZoneConfiguration.getBuckets()) {
      bucketMap.put(Region.of(bucket.getRegion()), bucket.getName());
    }

    return new AwsCloudContext(
        landingZoneConfiguration.getName(),
        landingZoneConfiguration.getAccountNumber(),
        Arn.fromString(landingZoneConfiguration.getServiceRoleArn()),
        serviceRoleAudience,
        Arn.fromString(landingZoneConfiguration.getUserRoleArn()),
        Arn.fromString(landingZoneConfiguration.getKmsKeyArn()),
        notebookLifecycleConfigArn,
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

      // Serialized context may not have a notebook lifecycle defined, so check for null before
      // attempting to construct an ARN.
      Arn notebookLifecycleConfigArn =
          (dbContext.notebookLifecycleConfigArn != null)
              ? Arn.fromString(dbContext.notebookLifecycleConfigArn)
              : null;

      Map<Region, String> bucketMap = new HashMap<>();
      for (AwsCloudContextBucketV1 bucket : dbContext.bucketList) {
        bucket.validateVersion();
        bucketMap.put(Region.of(bucket.regionName), bucket.bucketName);
      }

      return new AwsCloudContext(
          dbContext.landingZoneName,
          dbContext.accountNumber,
          Arn.fromString(dbContext.serviceRoleArn),
          dbContext.serviceRoleAudience,
          Arn.fromString(dbContext.userRoleArn),
          Arn.fromString(dbContext.kmsKeyArn),
          notebookLifecycleConfigArn,
          bucketMap);

    } catch (SerializationException e) {
      // Deserialization of V1 failed.
    }

    throw new InvalidSerializedVersionException("Invalid serialized version");
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
        throw new InvalidSerializedVersionException("Invalid serialized version");
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
    public List<AwsCloudContextBucketV1> bucketList;

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
        @JsonProperty("bucketList") List<AwsCloudContextBucketV1> bucketList) {
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
      this.serviceRoleArn = context.serviceRoleArn.toString();
      this.serviceRoleAudience = context.serviceRoleAudience;
      this.userRoleArn = context.userRoleArn.toString();
      this.kmsKeyArn = context.kmsKeyArn.toString();

      // Notebook Lifecycle Config is optional and may be null
      this.notebookLifecycleConfigArn =
          Optional.ofNullable(context.notebookLifecycleConfigArn).map(Arn::toString).orElse(null);

      this.bucketList = new ArrayList<>();
      for (Map.Entry<Region, String> entry : context.regionToBucketNameMap.entrySet()) {
        Region region = entry.getKey();
        String bucketName = entry.getValue();
        this.bucketList.add(new AwsCloudContextBucketV1(region.toString(), bucketName));
      }
    }

    public void validateVersion() {
      if (this.version != (AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK)) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }
    }
  }
}
