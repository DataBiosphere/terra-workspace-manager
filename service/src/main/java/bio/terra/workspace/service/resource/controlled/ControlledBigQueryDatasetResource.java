package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.exception.InvalidReferenceException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;

public class ControlledBigQueryDatasetResource extends ControlledResource {
  private final String datasetName;

  @JsonCreator
  public ControlledBigQueryDatasetResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("applicationId") UUID applicationId,
      @JsonProperty("datasetName") String datasetName) {

    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId);
    this.datasetName = datasetName;
    validate();
  }

  public ControlledBigQueryDatasetResource(DbResource dbResource) {
    super(dbResource);
    ControlledBigQueryDatasetAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledBigQueryDatasetAttributes.class);
    this.datasetName = attributes.getDatasetName();
    validate();
  }

  public static ControlledBigQueryDatasetResource.Builder builder() {
    return new ControlledBigQueryDatasetResource.Builder();
  }

  public String getDatasetName() {
    return datasetName;
  }

  public ApiGcpBigQueryDatasetAttributes toApiAttributes(String projectId) {
    return new ApiGcpBigQueryDatasetAttributes().projectId(projectId).datasetId(getDatasetName());
  }

  public ApiGcpBigQueryDatasetResource toApiResource(String projectId) {
    return new ApiGcpBigQueryDatasetResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes(projectId));
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.BIG_QUERY_DATASET;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(new ControlledBigQueryDatasetAttributes(getDatasetName()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.BIG_QUERY_DATASET) {
      throw new InconsistentFieldsException("Expected BIG_QUERY_DATASET");
    }
    if (getDatasetName() == null) {
      throw new MissingRequiredFieldException(
          "Missing required field datasetName for BigQuery dataset");
    }
    ValidationUtils.validateBqDatasetName(getDatasetName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledBigQueryDatasetResource that = (ControlledBigQueryDatasetResource) o;

    return (datasetName.equals(that.datasetName));
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetName);
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
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private UUID applicationId;
    private String datasetName;

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

    public Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
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
          accessScope,
          managedBy,
          applicationId,
          datasetName);
    }
  }
}
