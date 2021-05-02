package bio.terra.workspace.service.resource.controlled;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAttributes;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import javax.annotation.Nullable;

/** A {@link ControlledResource} for a Google AI Platform Notebook instance. */
public class ControlledAiNotebookInstanceResource extends ControlledResource {
  private final String instanceId;
  private final String location;

  @JsonCreator
  public ControlledAiNotebookInstanceResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("location") String location) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy);
    this.instanceId = instanceId;
    this.location = location;
    validate();
  }

  public ControlledAiNotebookInstanceResource(DbResource dbResource) {
    super(dbResource);
    ControlledAiNotebookInstanceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAiNotebookInstanceAttributes.class);
    this.instanceId = attributes.getInstanceId();
    this.location = attributes.getLocation();
    validate();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The user specified id of the notebook instance. */
  public String getInstanceId() {
    return instanceId;
  }

  /** The Google Cloud Platform location of the notebook instance, e.g. "us-east1-b". */
  public String getLocation() {
    return location;
  }

  public ApiGcpAiNotebookInstanceResource toApiResource(String workspaceProjectId) {
    return new ApiGcpAiNotebookInstanceResource()
        .metadata(toApiMetadata())
        .attributes(toApiAttributes(workspaceProjectId));
  }

  public ApiGcpAiNotebookInstanceAttributes toApiAttributes(String workspaceProjectId) {
    return new ApiGcpAiNotebookInstanceAttributes()
        .projectId(workspaceProjectId)
        .location(getLocation())
        .instanceId(getInstanceId());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.AI_NOTEBOOK_INSTANCE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAiNotebookInstanceAttributes(getInstanceId(), getLocation()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.AI_NOTEBOOK_INSTANCE) {
      throw new InconsistentFieldsException("Expected AI_NOTEBOOK_INSTANCE");
    }
    checkFieldNonNull(getInstanceId(), "instanceId");
    checkFieldNonNull(getLocation(), "location");
    ValidationUtils.validateAiNotebookInstanceId(getInstanceId());
  }

  private static <T> void checkFieldNonNull(@Nullable T fieldValue, String fieldName) {
    if (fieldValue == null) {
      throw new MissingRequiredFieldException(
          String.format("Missing required field '%s' for ControlledNotebookInstance.", fieldName));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAiNotebookInstanceResource that = (ControlledAiNotebookInstanceResource) o;

    return instanceId.equals(that.instanceId) && location.equals(that.location);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + instanceId.hashCode();
    result = 31 * result + location.hashCode();
    return result;
  }

  /** Builder for {@link ControlledAiNotebookInstanceResource}. */
  public static class Builder {
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String assignedUser;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private String instanceId;
    private String location;

    public Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder cloningInstructions(CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder location(String location) {
      this.location = location;
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

    public ControlledAiNotebookInstanceResource build() {
      return new ControlledAiNotebookInstanceResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          accessScope,
          managedBy,
          instanceId,
          location);
    }
  }
}
