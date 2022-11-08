package bio.terra.workspace.service.resource.referenced.cloud.gcp.bqdataset;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

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
      @JsonProperty("description") @Nullable String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("resourceLineage") @Nullable List<ResourceLineageEntry> resourceLineage,
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
    this.datasetName = datasetName;
    validate();
  }

  private ReferencedBigQueryDatasetResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.projectId = builder.projectId;
    this.datasetName = builder.datasetName;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedBigQueryDatasetResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET) {
      throw new InvalidMetadataException("Expected REFERENCED_BIG_QUERY_DATASET");
    }

    ReferencedBigQueryDatasetAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedBigQueryDatasetAttributes.class);
    this.projectId = attributes.getProjectId();
    this.datasetName = attributes.getDatasetName();
    validate();
  }

  public static ReferencedBigQueryDatasetResource.Builder builder() {
    return new ReferencedBigQueryDatasetResource.Builder();
  }

  public String getProjectId() {
    return projectId;
  }

  public String getDatasetName() {
    return datasetName;
  }

  public ApiGcpBigQueryDatasetAttributes toApiAttributes() {
    return new ApiGcpBigQueryDatasetAttributes()
        .projectId(getProjectId())
        .datasetId(getDatasetName());
  }

  public ApiGcpBigQueryDatasetResource toApiResource() {
    return new ApiGcpBigQueryDatasetResource()
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
    return WsmResourceType.REFERENCED_GCP_BIG_QUERY_DATASET;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.BIG_QUERY_DATASET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedBigQueryDatasetAttributes(getProjectId(), getDatasetName()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpBqDataset(toApiAttributes());
  }

  @Override
  public ApiResourceUnion toApiResourceUnion() {
    return new ApiResourceUnion().gcpBqDataset(toApiResource());
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getProjectId()) || Strings.isNullOrEmpty(getDatasetName())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceBigQueryDatasetAttributes.");
    }
    ResourceValidationUtils.validateBqDatasetName(getDatasetName());
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    // If the resource's workspace has a GCP cloud context, use the SA from that context. Otherwise,
    // use the provided credentials. This cannot use arbitrary pet SA credentials, as they may not
    // have the BigQuery APIs enabled.
    CrlService crlService = context.getCrlService();
    PetSaService petSaService = context.getPetSaService();
    Optional<AuthenticatedUserRequest> maybePetCreds =
        petSaService.getWorkspacePetCredentials(getWorkspaceId(), userRequest);
    return crlService.canReadBigQueryDataset(
        projectId, datasetName, maybePetCreds.orElse(userRequest));
  }

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description) {
    ReferencedBigQueryDatasetResource.Builder resultBuilder =
        toBuilder()
            .wsmResourceFields(
                buildReferencedCloneResourceCommonFields(
                    destinationWorkspaceUuid,
                    destinationResourceId,
                    destinationFolderId,
                    name,
                    description));
    return resultBuilder.build();
  }

  /**
   * Build a builder with values from this object. This is useful when creating related objects that
   * share several values.
   *
   * @return - a Builder for a new ReferencedBigQueryDatasetResource
   */
  public Builder toBuilder() {
    return builder()
        .wsmResourceFields(getWsmResourceFields())
        .datasetName(getDatasetName())
        .projectId(getProjectId());
  }

  public static class Builder {
    private String projectId;
    private String datasetName;
    private WsmResourceFields wsmResourceFields;

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder datasetName(String datasetName) {
      this.datasetName = datasetName;
      return this;
    }

    public Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public ReferencedBigQueryDatasetResource build() {
      // On the create path, we can omit the resourceId and have it filled in by the builder.
      return new ReferencedBigQueryDatasetResource(this);
    }
  }
}
