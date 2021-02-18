package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.resource.controlled.CloudPlatform;
import bio.terra.workspace.service.resource.controlled.WsmControlledResourceWithApiModels;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class ControlledGcsBucketResource
    extends WsmControlledResourceWithApiModels<
        GoogleBucketCreationParameters, GoogleBucketStoredAttributes> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @JsonCreator
  public ControlledGcsBucketResource(
      @JsonProperty("resourceName") String resourceName,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("description") String description,
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("owner") String owner,
      @JsonProperty("inputModel") GoogleBucketCreationParameters inputModel) {
    super(resourceName, cloningInstructions, description, workspaceId, owner, inputModel);
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
    return getApiInputModel().getName();
  }

  public String getLocation() {
    return getApiInputModel().getLocation();
  }

  public GoogleBucketDefaultStorageClass getDefaultStorageClass() {
    return getApiInputModel().getDefaultStorageClass();
  }

  public GoogleBucketLifecycle getLifecycle() {
    return getApiInputModel().getLifecycle();
  }

  @Override
  public String getJsonAttributes() {
    // In this case, the attributes and output model match exactly. I'm
    // not yet sure I wan† to enforce that constraint at the top level.
    try {
      return objectMapper.writeValueAsString(toOutputApiModel());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to write to JSON");
    }
  }

  @Override
  public GoogleBucketStoredAttributes toOutputApiModel() {
    return new GoogleBucketStoredAttributes().bucketName(getApiInputModel().getName());
  }
}
