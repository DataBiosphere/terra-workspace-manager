package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.resource.controlled.CloudPlatform;
import bio.terra.workspace.service.resource.controlled.WsmControlledResource;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class ControlledGcsBucketResource
    extends WsmControlledResource<GoogleBucketCreationParameters, GoogleBucketStoredAttributes> {
  private final String bucketName;
  private final String location;
  private final GoogleBucketDefaultStorageClass defaultStorageClass;
  private final GoogleBucketLifecycle lifecycle;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ControlledGcsBucketResource(
      String resourceName,
      String description,
      UUID resourceId,
      UUID workspaceId,
      boolean isVisible,
      String associatedApp,
      String owner,
      GoogleBucketCreationParameters params) {
    super(
        resourceName,
        description,
        resourceId,
        workspaceId,
        isVisible,
        associatedApp,
        params,
        owner);
    this.bucketName = params.getName();
    this.location = params.getLocation();
    this.defaultStorageClass = params.getDefaultStorageClass();
    this.lifecycle = params.getLifecycle();
  }

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.GCP;
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.BUCKET;
  }

  public String getBucketName() {
    return bucketName;
  }

  public String getLocation() {
    return location;
  }

  public GoogleBucketDefaultStorageClass getDefaultStorageClass() {
    return defaultStorageClass;
  }

  public GoogleBucketLifecycle getLifecycle() {
    return lifecycle;
  }

  @Override
  public String getJsonAttributes() {
    // In this case, the attributes and outdput model match exactly. I'm
    // not yet sure I wan† to enforce that constraint at the top level.
    try {
      return objectMapper.writeValueAsString(toOutputApiModel());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to write to JSON");
    }
  }

  @Override
  public GoogleBucketStoredAttributes toOutputApiModel() {
    return new GoogleBucketStoredAttributes().bucketName(this.bucketName);
  }
}
