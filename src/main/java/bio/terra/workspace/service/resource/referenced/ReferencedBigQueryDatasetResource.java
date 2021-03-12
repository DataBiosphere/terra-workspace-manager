package bio.terra.workspace.service.resource.referenced;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiBigQueryDatasetReference;
import bio.terra.workspace.generated.model.ApiGoogleBigQueryDatasetUid;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;

public class ReferencedBigQueryDatasetResource extends ReferencedResource {
  private final String projectId;
  private final String datasetName;

  /**
   * Constructor for serialized form for Stairway use
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param projectId google project id
   * @param datasetName BigQuery dataset name
   */
  @JsonCreator
  public ReferencedBigQueryDatasetResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.projectId = projectId;
    this.datasetName = datasetName;
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedBigQueryDatasetResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.BIG_QUERY_DATASET) {
      throw new InvalidMetadataException("Expected BIG_QUERY_DATASET");
    }

    ReferencedBigQueryDatasetAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedBigQueryDatasetAttributes.class);
    this.projectId = attributes.getProjectId();
    this.datasetName = attributes.getDatasetName();
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public ApiBigQueryDatasetReference toApiModel() {
    return new ApiBigQueryDatasetReference()
        .metadata(super.toApiMetadata())
        .dataset(
            new ApiGoogleBigQueryDatasetUid()
                .projectId(getProjectId())
                .datasetId(getDatasetName()));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.BIG_QUERY_DATASET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedBigQueryDatasetAttributes(getProjectId(), getDatasetName()));
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getProjectId()) || Strings.isNullOrEmpty(getDatasetName())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceBigQueryDatasetAttributes.");
    }
    ValidationUtils.validateBqDatasetName(getDatasetName());
  }

  public static ReferencedBigQueryDatasetResource.Builder builder() {
    return new ReferencedBigQueryDatasetResource.Builder();
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String projectId;
    private String datasetName;

    public ReferencedBigQueryDatasetResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ReferencedBigQueryDatasetResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferencedBigQueryDatasetResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferencedBigQueryDatasetResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferencedBigQueryDatasetResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder datasetName(String datasetName) {
      this.datasetName = datasetName;
      return this;
    }

    public ReferencedBigQueryDatasetResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedBigQueryDatasetResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          projectId,
          datasetName);
    }
  }
}
