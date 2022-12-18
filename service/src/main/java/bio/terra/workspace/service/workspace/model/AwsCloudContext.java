package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
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
  private final String landingZoneName;
  private final String accountNumber;
  private final Arn serviceRoleArn;
  private final String serviceRoleAudience;
  private final Arn userRoleArn;
  private final Arn notebookLifecycleConfigArn;
  private final Map<Region, String> regionToBucketNameMap;

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

  public Arn getNotebookLifecycleConfigArn() {
    return notebookLifecycleConfigArn;
  }

  public @Nullable String getBucketNameForRegion(Region region) {
    return regionToBucketNameMap.get(region);
  }

  public static @Nullable AwsCloudContext fromConfiguration(
      AwsConfiguration.AwsLandingZoneConfiguration landingZoneConfiguration,
      String serviceRoleAudience) {

    AwsCloudContext.Builder builder =
        AwsCloudContext.builder()
            .landingZoneName(landingZoneConfiguration.getName())
            .accountNumber(landingZoneConfiguration.getAccountNumber())
            .serviceRoleArn(Arn.fromString(landingZoneConfiguration.getServiceRoleArn()))
            .serviceRoleAudience(serviceRoleAudience)
            .userRoleArn(Arn.fromString(landingZoneConfiguration.getUserRoleArn()));

    // Configuration Lifecycle may not be configured, so check for null before attempting to
    // construct an ARN.
    Optional.ofNullable(landingZoneConfiguration.getNotebookLifecycleConfigArn())
        .map(arn -> builder.notebookLifecycleConfigArn(Arn.fromString(arn)));

    for (AwsConfiguration.AwsLandingZoneBucket bucket : landingZoneConfiguration.getBuckets()) {
      builder.addBucket(Region.of(bucket.getRegion()), bucket.getName());
    }

    return builder.build();
  }

  public ApiAwsContext toApi() {
    return new ApiAwsContext().landingZoneId(landingZoneName).accountNumber(accountNumber);
  }

  public String serialize() {
    AwsCloudContextV1 dbContext = new AwsCloudContextV1(this);
    return DbSerDes.toJson(dbContext);
  }

  public static AwsCloudContext deserialize(String json) {
    AwsCloudContextV1 dbContext = DbSerDes.fromJson(json, AwsCloudContextV1.class);
    dbContext.validateVersion();

    Builder builder =
        builder()
            .landingZoneName(dbContext.landingZoneName)
            .accountNumber(dbContext.accountNumber)
            .serviceRoleArn(Arn.fromString(dbContext.serviceRoleArn))
            .serviceRoleAudience(dbContext.serviceRoleAudience)
            .userRoleArn(Arn.fromString(dbContext.userRoleArn));

    // Serialized context may not have a notebook lifecycle defined, so check for null before
    // attempting to construct an ARN.
    Optional.ofNullable(dbContext.notebookLifecycleConfigArn)
        .map(arn -> builder.notebookLifecycleConfigArn(Arn.fromString(arn)));

    for (AwsCloudContextBucketV1 bucket : dbContext.bucketList) {
      bucket.validateVersion();
      builder.addBucket(Region.of(bucket.regionName), bucket.bucketName);
    }

    return builder.build();
  }

  public static class Builder {
    private String landingZoneName;
    private String accountNumber;
    private Arn serviceRoleArn;
    private String serviceRoleAudience;
    private Arn userRoleArn;
    private Arn notebookLifecycleConfigArn;
    private Map<Region, String> bucketMap;

    private Builder() {
      bucketMap = new HashMap<>();
    }

    public AwsCloudContext build() {
      return new AwsCloudContext(this);
    }

    public Builder landingZoneName(String landingZoneName) {
      this.landingZoneName = landingZoneName;
      return this;
    }

    public Builder accountNumber(String accountNumber) {
      this.accountNumber = accountNumber;
      return this;
    }

    public Builder serviceRoleArn(Arn serviceRoleArn) {
      this.serviceRoleArn = serviceRoleArn;
      return this;
    }

    public Builder serviceRoleAudience(String serviceRoleAudience) {
      this.serviceRoleAudience = serviceRoleAudience;
      return this;
    }

    public Builder userRoleArn(Arn userRoleArn) {
      this.userRoleArn = userRoleArn;
      return this;
    }

    public Builder notebookLifecycleConfigArn(Arn notebookLifecycleConfigArn) {
      this.notebookLifecycleConfigArn = notebookLifecycleConfigArn;
      return this;
    }

    public Builder addBucket(Region region, String bucketName) {
      bucketMap.put(region, bucketName);
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private AwsCloudContext(Builder builder) {
    this.landingZoneName = builder.landingZoneName;
    this.accountNumber = builder.accountNumber;
    this.serviceRoleArn = builder.serviceRoleArn;
    this.serviceRoleAudience = builder.serviceRoleAudience;
    this.userRoleArn = builder.userRoleArn;
    this.notebookLifecycleConfigArn = builder.notebookLifecycleConfigArn;
    this.regionToBucketNameMap = builder.bucketMap;
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
    public String notebookLifecycleConfigArn;
    public List<AwsCloudContextBucketV1> bucketList;

    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("landingZoneName") String landingZoneName,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("serviceRoleArn") String serviceRoleArn,
        @JsonProperty("serviceRoleAudience") String serviceRoleAudience,
        @JsonProperty("userRoleArn") String userRoleArn,
        @JsonProperty("notebookLifecycleConfigArn") String notebookLifecycleConfigArn,
        @JsonProperty("bucketList") List<AwsCloudContextBucketV1> bucketList) {
      this.version = version;
      this.landingZoneName = landingZoneName;
      this.accountNumber = accountNumber;
      this.serviceRoleArn = serviceRoleArn;
      this.serviceRoleAudience = serviceRoleAudience;
      this.userRoleArn = userRoleArn;
      this.notebookLifecycleConfigArn = notebookLifecycleConfigArn;
      this.bucketList = bucketList;
    }

    public AwsCloudContextV1(AwsCloudContext context) {
      this.version = AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_DB_VERSION_MASK;
      this.landingZoneName = context.landingZoneName.toString();
      this.accountNumber = context.accountNumber;
      this.serviceRoleArn = context.serviceRoleArn.toString();
      this.serviceRoleAudience = context.serviceRoleAudience;
      this.userRoleArn = context.userRoleArn.toString();

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
