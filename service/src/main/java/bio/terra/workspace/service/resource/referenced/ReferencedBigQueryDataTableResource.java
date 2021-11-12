package bio.terra.workspace.service.resource.referenced;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.Optional;
import java.util.UUID;

public class ReferencedBigQueryDataTableResource extends ReferencedResource {
  private final String projectId;
  private final String datasetName;
  private final String dataTableName;

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
   * @param dataTableName BigQuery dataset's data table name
   */
  @JsonCreator
  public ReferencedBigQueryDataTableResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("dataTableName") String dataTableName) {
    super(workspaceId, resourceId, name, description, cloningInstructions);
    this.projectId = projectId;
    this.datasetName = datasetName;
    this.dataTableName = dataTableName;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedBigQueryDataTableResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.BIG_QUERY_DATASET) {
      throw new InvalidMetadataException("Expected BIG_QUERY_DATASET");
    }

    ReferencedBigQueryDataTableAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedBigQueryDataTableAttributes.class);
    this.projectId = attributes.getProjectId();
    this.datasetName = attributes.getDataTableName();
    this.dataTableName = attributes.getDataTableName();
    validate();
  }

  public static ReferencedBigQueryDataTableResource.Builder builder() {
    return new ReferencedBigQueryDataTableResource.Builder();
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getDataTableName() {
    return dataTableName;
  }

  public ApiGcpBigQueryDataTableAttributes toApiAttributes() {
    return new ApiGcpBigQueryDataTableAttributes()
        .projectId(getProjectId())
        .datasetId(getDatasetName())
        .dataTableId(getDataTableName());
  }

  public ApiGcpBigQueryDataTableResource toApiResource() {
    return new ApiGcpBigQueryDataTableResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.BIG_QUERY_DATATABLE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedBigQueryDataTableAttributes(
            getProjectId(), getDatasetName(), getDataTableName()));
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getProjectId()) || Strings.isNullOrEmpty(getDataTableName())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceBigQueryDataTableAttributes.");
    }
    ValidationUtils.validateBqDatasetName(getDataTableName());
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    CrlService crlService = context.getCrlService();
    return crlService.canReadBigQueryDataset(projectId, datasetName, userRequest);
  }

  /**
   * Build a builder with values from this object. This is useful when creating related objects that
   * share several values.
   *
   * @return - a Builder for a new ReferencedBigQueryDataTableResource
   */
  public Builder toBuilder() {
    return builder()
        .cloningInstructions(getCloningInstructions())
        .datasetName(getDataTableName())
        .description(getDescription())
        .name(getName())
        .projectId(getProjectId())
        .resourceId(getResourceId())
        .workspaceId(getWorkspaceId());
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String projectId;
    private String datasetName;
    private String dataTableName;

    public ReferencedBigQueryDataTableResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ReferencedBigQueryDataTableResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ReferencedBigQueryDataTableResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ReferencedBigQueryDataTableResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ReferencedBigQueryDataTableResource.Builder cloningInstructions(
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

    public Builder dataTableName(String dataTableName) {
      this.dataTableName = dataTableName;
      return this;
    }

    public ReferencedBigQueryDataTableResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedBigQueryDataTableResource(
          workspaceId,
          Optional.ofNullable(resourceId).orElse(UUID.randomUUID()),
          name,
          description,
          cloningInstructions,
          projectId,
          datasetName,
          dataTableName);
    }
  }
}
