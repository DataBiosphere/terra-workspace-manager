package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdatatable;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDataTableResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedBigQueryDataTableResource extends ReferencedResource {

  private final String projectId;
  private final String datasetId;
  private final String dataTableId;

  /**
   * Constructor for serialized form for Stairway use
   *
   * @param workspaceId workspace unique identifier
   * @param resourceId resource unique identifier
   * @param name name resource name; unique within a workspace
   * @param description description - may be null
   * @param cloningInstructions cloning instructions
   * @param projectId google project id
   * @param datasetId BigQuery dataset name
   * @param dataTableId BigQuery dataset's data table name
   * @param resourceLineage resource lineage
   */
  @JsonCreator
  public ReferencedBigQueryDataTableResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetId") String datasetId,
      @JsonProperty("dataTableId") String dataTableId,
      @JsonProperty("resourceLineage") List<ResourceLineageEntry> resourceLineage,
      @JsonProperty("properties") Map<String, String> properties) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        resourceLineage,
        properties);
    this.projectId = projectId;
    this.datasetId = datasetId;
    this.dataTableId = dataTableId;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedBigQueryDataTableResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE) {
      throw new InvalidMetadataException("Expected REFERENCED_GCP BIG_QUERY_DATA_TABLE");
    }

    ReferencedBigQueryDataTableAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedBigQueryDataTableAttributes.class);
    this.projectId = attributes.getProjectId();
    this.datasetId = attributes.getDatasetId();
    this.dataTableId = attributes.getDataTableId();
    validate();
  }

  private ReferencedBigQueryDataTableResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.projectId = builder.projectId;
    this.datasetId = builder.datasetId;
    this.dataTableId = builder.dataTableId;
    validate();
  }

  public static ReferencedBigQueryDataTableResource.Builder builder() {
    return new ReferencedBigQueryDataTableResource.Builder();
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public String getDataTableId() {
    return dataTableId;
  }

  public ApiGcpBigQueryDataTableAttributes toApiAttributes() {
    return new ApiGcpBigQueryDataTableAttributes()
        .projectId(getProjectId())
        .datasetId(getDatasetId())
        .dataTableId(getDataTableId());
  }

  public ApiGcpBigQueryDataTableResource toApiResource() {
    return new ApiGcpBigQueryDataTableResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATA_TABLE;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.BIG_QUERY_DATA_TABLE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedBigQueryDataTableAttributes(
            getProjectId(), getDatasetId(), getDataTableId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpBqDataTable(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gcpBqDataTable(toApiResource());
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getProjectId())
        || Strings.isNullOrEmpty(getDatasetId())
        || Strings.isNullOrEmpty(getDataTableId())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceBigQueryDataTableAttributes");
    }
    ResourceValidationUtils.validateBqDatasetName(getDatasetId());
    ResourceValidationUtils.validateBqDataTableName(getDataTableId());
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    CrlService crlService = context.getCrlService();
    PetSaService petSaService = context.getPetSaService();
    Optional<AuthenticatedUserRequest> maybePetCreds =
        petSaService.getWorkspacePetCredentials(getWorkspaceId(), userRequest);
    return crlService.canReadBigQueryDataTable(
        projectId, datasetId, dataTableId, maybePetCreds.orElse(userRequest));
  }

  /**
   * Build a builder with values from this object. This is useful when creating related objects that
   * share several values.
   *
   * @return - a Builder for a new ReferencedBigQueryDataTableResource
   */
  public Builder toBuilder() {
    return builder()
        .wsmResourceFields(getWsmResourceFields())
        .datasetId(getDatasetId())
        .dataTableId(getDataTableId())
        .projectId(getProjectId());
  }

  public static class Builder {

    private String projectId;
    private String datasetId;
    private String dataTableId;
    private WsmResourceFields wsmResourceFields;

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder datasetId(String datasetId) {
      this.datasetId = datasetId;
      return this;
    }

    public Builder dataTableId(String dataTableId) {
      this.dataTableId = dataTableId;
      return this;
    }

    public Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public ReferencedBigQueryDataTableResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedBigQueryDataTableResource(this);
    }
  }
}
