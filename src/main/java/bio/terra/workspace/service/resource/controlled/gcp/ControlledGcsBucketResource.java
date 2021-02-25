package bio.terra.workspace.service.resource.controlled.gcp;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketStoredAttributes;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.GoogleBucketReference;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import bio.terra.workspace.service.resource.controlled.ControlledResourceWithApiModels;
import bio.terra.workspace.service.resource.controlled.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import java.util.Objects;
import java.util.UUID;

public class ControlledGcsBucketResource
    extends ControlledResourceWithApiModels<
        GoogleBucketCreationParameters, GoogleBucketStoredAttributes> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
  public WsmResourceType getResourceType() {
    return WsmResourceType.GCS_BUCKET;
  }

  // TODO: these may not be strictly needed yet, but it seems reasonable to expose them here.
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
      return OBJECT_MAPPER.writeValueAsString(toOutputApiModel());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to write to JSON");
    }
  }

  @Override
  public GoogleBucketStoredAttributes toOutputApiModel() {
    return new GoogleBucketStoredAttributes().bucketName(getApiInputModel().getName());
  }

  @Override
  public ReferenceObject getReferenceObject() {
    return GoogleBucketReference.create(getBucketName());
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getBucketName())
        || getDefaultStorageClass() == null
        || getLifecycle() == null
        || getLocation() == null
        || getJsonAttributes() == null) {
      throw new IllegalStateException("Missing required field for ControlledGcsBucketResource.");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ControlledGcsBucketResource)) {
      return false;
    }
    return super.equals(o); // no fields in this class
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode());
  }

  public static GoogleBucketStoredAttributes attributesToOutputApiModel(String jsonAttributes) {
    try {
      return OBJECT_MAPPER.readValue(jsonAttributes, GoogleBucketStoredAttributes.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          String.format("Could not parse JSON attributes string %s", jsonAttributes));
    }
  }
}
