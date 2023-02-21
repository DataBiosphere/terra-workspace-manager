package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.workspace.generated.model.ApiAwsCredentialAccessScope;
import bio.terra.workspace.generated.model.ApiAwsSagemakerNotebookDefaultBucket;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAwsSageMakerNotebookAttributes {

  @JsonCreator
  public ControlledAwsSageMakerNotebookAttributes(
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("region") String region,
      @JsonProperty("instanceType") String instanceType,
      @JsonProperty("defaultBucket") DefaultBucket defaultBucket) {
    this.instanceId = instanceId;
    this.region = region;
    this.instanceType = instanceType;
    this.defaultBucket = defaultBucket;
  }

  public static class DefaultBucket {
    private final String bucketId;
    private final String accessScope;

    public DefaultBucket(
        @JsonProperty("bucketId") String bucketId,
        @JsonProperty("accessScope") String accessScope) {
      this.bucketId = bucketId;
      this.accessScope = accessScope;
    }

    public DefaultBucket(ApiAwsSagemakerNotebookDefaultBucket defaultBucket) {
      this.bucketId = defaultBucket.getBucketId().toString();
      this.accessScope = defaultBucket.getAccessScope().toString();
    }

    public String getBucketId() {
      return bucketId;
    }

    public String getAccessScope() {
      return accessScope;
    }

    ApiAwsSagemakerNotebookDefaultBucket toApi() {
      return new ApiAwsSagemakerNotebookDefaultBucket()
          .bucketId(UUID.fromString(this.bucketId))
          .accessScope(ApiAwsCredentialAccessScope.fromValue(this.accessScope));
    }
  }

  private final String instanceId;
  private final String region;
  private final String instanceType;
  private final DefaultBucket defaultBucket;

  public String getInstanceId() {
    return instanceId;
  }

  public String getRegion() {
    return region;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public DefaultBucket getDefaultBucket() {
    return defaultBucket;
  }
}
