package bio.terra.workspace.service.resource.reference;

import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.BigQueryDatasetReference;
import bio.terra.workspace.generated.model.GoogleBigQueryDatasetUid;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class ReferenceBigQueryDatasetResource extends ReferenceResource {
  private final ReferenceBigQueryDatasetAttributes attributes;

  /**
   * Constructor for serialized form for Stairway use
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name - may be null
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param projectId google project id
   * @param datasetName BigQuery dataset name
   */
  @JsonCreator
  public ReferenceBigQueryDatasetResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.attributes = new ReferenceBigQueryDatasetAttributes(projectId, datasetName);
  }

  /**
   * Constructor from database metadata
   * @param dbResource database form of resources
   */
  public ReferenceBigQueryDatasetResource(DbResource dbResource) {
    super(
        dbResource.getWorkspaceId(),
        dbResource.getResourceId(),
        dbResource.getName().orElse(null),
        dbResource.getDescription().orElse(null),
        dbResource.getCloningInstructions());

    if (dbResource.getResourceType() != WsmResourceType.GCS_BUCKET) {
      throw new InvalidMetadataException("Expected GCS_BUCKET");
    }

    this.attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferenceBigQueryDatasetAttributes.class);
  }

  public ReferenceBigQueryDatasetAttributes getAttributes() {
    return attributes;
  }

  public BigQueryDatasetReference toApiModel() {
    return new BigQueryDatasetReference()
            .metadata(super.toApiMetadata())
            .dataset(new GoogleBigQueryDatasetUid()
                    .projectId(getAttributes().getProjectId())
                    .datasetId(getAttributes().getDatasetName()));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.BIG_QUERY_DATASET;
  }

  @Override
  public String getJsonAttributes() {
    return DbSerDes.toJson(attributes);
  }

  @Override
  public void validate() {
    super.validate();
    if (getAttributes() == null
            || getAttributes().getProjectId() == null
            || getAttributes().getDatasetName() == null) {
      throw new MissingRequiredFieldException("Missing required field for ReferenceBigQueryDatasetAttributes.");
    }
    ValidationUtils.validateBqDatasetName(getAttributes().getDatasetName());
  }
}
