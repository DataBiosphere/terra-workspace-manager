package bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.UniquenessCheckParameters;
import bio.terra.workspace.db.model.UniquenessCheckParameters.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ControlledBigQueryDatasetResource extends ControlledResource {
  private final String datasetName;
  private final String projectId;

  @JsonCreator
  public ControlledBigQueryDatasetResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") UUID applicationId,
      @JsonProperty("datasetName") String datasetName,
      @JsonProperty("projectId") String projectId) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState);
    this.datasetName = datasetName;
    this.projectId = projectId;
    validate();
  }

  public static ControlledBigQueryDatasetResource.Builder builder() {
    return new ControlledBigQueryDatasetResource.Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .workspaceId(getWorkspaceId())
        .resourceId(getResourceId())
        .name(getName())
        .description(getDescription())
        .cloningInstructions(getCloningInstructions())
        .assignedUser(getAssignedUser().orElse(null))
        .privateResourceState(getPrivateResourceState().orElse(null))
        .accessScope(getAccessScope())
        .managedBy(getManagedBy())
        .applicationId(getApplicationId())
        .datasetName(getDatasetName())
        .projectId(projectId);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<UniquenessCheckParameters> getUniquenessCheckParameters() {
    return Optional.of(
        new UniquenessCheckParameters(UniquenessScope.WORKSPACE)
            .addParameter("datasetName", getDatasetName()));
  }

  public String getDatasetName() {
    return datasetName;
  }

  public String getProjectId() {
    return projectId;
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

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.BIG_QUERY_DATASET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledBigQueryDatasetAttributes(getDatasetName(), getProjectId()));
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
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET
        || getResourceFamily() != WsmResourceFamily.BIG_QUERY_DATASET
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP BIG_QUERY_DATASET");
    }
    if (getDatasetName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field datasetName for BigQuery dataset");
    }
    if (getProjectId() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field projectId for BigQuery dataset");
    }
    ValidationUtils.validateBqDatasetName(getDatasetName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ControlledBigQueryDatasetResource that = (ControlledBigQueryDatasetResource) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(datasetName, that.datasetName)
        .append(projectId, that.projectId)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(datasetName)
        .append(projectId)
        .toHashCode();
  }

  private static String generateUniqueDatasetId() {
    return "terra_" + UUID.randomUUID() + "_dataset".replace("-", "_");
  }

  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String assignedUser;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private UUID applicationId;
    private String datasetName;
    private String projectId;

    public ControlledBigQueryDatasetResource.Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder name(String name) {
      this.name = name;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder description(String description) {
      this.description = description;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder cloningInstructions(
        CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder datasetName(String datasetName) {
      try {
        // If the user doesn't specify a dataset name, we will use the resource name by default.
        // But if the resource name is not a valid dataset name, we will need to generate a unique
        // dataset id.
        ValidationUtils.validateBqDatasetName(datasetName);
        this.datasetName = datasetName;
      } catch (InvalidReferenceException e) {
        this.datasetName = generateUniqueDatasetId();
      }
      return this;
    }

    public ControlledBigQueryDatasetResource.Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public Builder privateResourceState(PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    private PrivateResourceState defaultPrivateResourceState() {
      return this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
          ? PrivateResourceState.INITIALIZING
          : PrivateResourceState.NOT_APPLICABLE;
    }

    public Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public Builder applicationId(UUID applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public ControlledBigQueryDatasetResource build() {
      return new ControlledBigQueryDatasetResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          Optional.ofNullable(privateResourceState).orElse(defaultPrivateResourceState()),
          accessScope,
          managedBy,
          applicationId,
          datasetName,
          projectId);
    }
  }
}
