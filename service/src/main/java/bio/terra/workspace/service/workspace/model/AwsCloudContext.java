package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiAwsContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class AwsCloudContext {
  /** Mask AWS version numbers so as not to collide with Azure and GCP version numbers */
  public static final long AWS_CLOUD_CONTEXT_VERSION_MASK = 0x100;

  private String awsAccountNumber;
  private String awsRegion;

  // Constructor for Jackson
  public AwsCloudContext() {}

  // Constructor for deserializer
  public AwsCloudContext(String awsAccountNumber, String awsRegion) {
    this.awsAccountNumber = awsAccountNumber;
    this.awsRegion = awsRegion;
  }

  public String getAwsAccountNumber() {
    return awsAccountNumber;
  }

  public String getAwsRegion() {
    return awsRegion;
  }

  public String setAwsAccountNumber(String awsAccountNumber) {
    return awsAccountNumber;
  }

  public String setAwsRegion(String awsRegion) {
    return awsRegion;
  }

  public ApiAwsContext toApi() {
    return new ApiAwsContext().accountNumber(awsAccountNumber).region(awsRegion);
  }

  // TODO: uncomment if needed
  /*
  public AwsCloudContext fromApi(ApiAwsContext awsContext) {
      return new AwsCloudContext(awsContext.getAccountNumber(), awsContext.getRegion());
  }

  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      }

      if (o == null || getClass() != o.getClass()) {
          return false;
      }

      AwsCloudContext that = (AwsCloudContext) o;
      return new EqualsBuilder()
              .append(awsAccountNumber, that.awsAccountNumber)
              .append(awsRegion, that.awsRegion)
              .isEquals();
  }

  @Override
  public int hashCode() {
      return new HashCodeBuilder(17, 37)
          .append(awsAccountNumber)
          .append(awsRegion)
          .toHashCode();
   }
   */

  /*
     public static @Nullable AwsCloudContext fromConfiguration(
             AwsConfiguration.AwsLandingZoneConfiguration landingZoneConfiguration,
             String serviceRoleAudience) {

         AwsCloudContext.Builder builder =
                 AwsCloudContext.builder()
                         .landingZoneName(landingZoneConfiguration.getName())
                         .accountNumber(landingZoneConfiguration.getAccountNumber())
                         .serviceRoleArn(Arn.fromString(landingZoneConfiguration.getServiceRoleArn()))
                         .serviceRoleAudience(serviceRoleAudience)
                         .userRoleArn(Arn.fromString(landingZoneConfiguration.getUserRoleArn()))
                         .kmsKeyArn(Arn.fromString(landingZoneConfiguration.getKmsKeyArn()));

         // Configuration Lifecycle may not be configured, so check for null before attempting to
         // construct an ARN.
         Optional.ofNullable(landingZoneConfiguration.getNotebookLifecycleConfigArn())
                 .map(arn -> builder.notebookLifecycleConfigArn(Arn.fromString(arn)));

         for (AwsConfiguration.AwsLandingZoneBucket bucket : landingZoneConfiguration.getBuckets()) {
             builder.addBucket(Region.of(bucket.getRegion()), bucket.getName());
         }

         return builder.build();
     }
  */

  public String serialize() {
    return DbSerDes.toJson(new AwsCloudContextV1(this));
  }

  public static AwsCloudContext deserialize(String json) {
    AwsCloudContextV1 v1Context = DbSerDes.fromJson(json, AwsCloudContextV1.class);
    if (v1Context.version != AwsCloudContextV1.getVersion()) {
      throw new InvalidSerializedVersionException("Invalid serialized version");
    }

    return new AwsCloudContext(v1Context.awsAccountNumber, v1Context.awsRegion);
  }

  @VisibleForTesting
  public static class AwsCloudContextV1 {
    private static final long AWS_CLOUD_CONTEXT_DB_VERSION = 1;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String awsAccountNumber;
    public String awsRegion;

    public AwsCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("awsAccountNumber") String awsAccountNumber,
        @JsonProperty("awsRegion") String awsRegion) {
      this.version = version;
      this.awsAccountNumber = awsAccountNumber;
      this.awsRegion = awsRegion;
    }

    public static long getVersion() {
      return AWS_CLOUD_CONTEXT_DB_VERSION | AWS_CLOUD_CONTEXT_VERSION_MASK;
    }

    public AwsCloudContextV1(AwsCloudContext context) {
      this.version = getVersion();
      this.awsAccountNumber = context.awsAccountNumber;
      this.awsRegion = context.awsRegion;
    }
  }
}
