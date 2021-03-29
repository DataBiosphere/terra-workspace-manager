package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.common.exception.InconsistentFieldsException;
import bio.terra.workspace.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;

/** A {@link ControlledResource} for a Google AI Platform Notebook instance. */
public class ControlledAiNotebookInstanceResource extends ControlledResource {
    private final String instanceName;

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
            @JsonProperty("instanceName") String instanceName) {
        super(
                workspaceId,
                resourceId,
                name,
                description,
                cloningInstructions,
                assignedUser,
                accessScope,
                managedBy);
        this.instanceName = instanceName;
        validate();
    }

    public ControlledAiNotebookInstanceResource(DbResource dbResource) {
        super(dbResource);
        ControlledAiNotebookInstanceAttributes attributes =
                DbSerDes.fromJson(dbResource.getAttributes(), ControlledAiNotebookInstanceAttributes.class);
        this.instanceName = attributes.getInstanceName();
        validate();
    }

    public static ControlledAiNotebookInstanceResource.Builder builder() {
        return new ControlledAiNotebookInstanceResource.Builder();
    }

    public String getInstanceName() {
        return instanceName;
    }

    // TODO(PF-469): Add conversion to API model.

    @Override
    public WsmResourceType getResourceType() {
        return WsmResourceType.AI_NOTEBOOK_INSTANCE;
    }

    @Override
    public String attributesToJson() {
        return DbSerDes.toJson(new ControlledAiNotebookInstanceAttributes(getInstanceName()));
    }

    @Override
    public void validate() {
        super.validate();
        if (getResourceType() != WsmResourceType.AI_NOTEBOOK_INSTANCE) {
            throw new InconsistentFieldsException("Expected AI_NOTEBOOK_INSTANCE");
        }
        if (getInstanceName() == null) {
            throw new MissingRequiredFieldException("Missing required field instanceName for ControlledNotebookInstance.");
        }
        ValidationUtils.validateAiNotebookInstanceName(getInstanceName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ControlledAiNotebookInstanceResource that = (ControlledAiNotebookInstanceResource) o;

        return instanceName.equals(that.instanceName);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + instanceName.hashCode();
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
        private String instanceName;

        public ControlledAiNotebookInstanceResource.Builder workspaceId(UUID workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public ControlledAiNotebookInstanceResource.Builder resourceId(UUID resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public ControlledAiNotebookInstanceResource.Builder name(String name) {
            this.name = name;
            return this;
        }

        public ControlledAiNotebookInstanceResource.Builder description(String description) {
            this.description = description;
            return this;
        }

        public ControlledAiNotebookInstanceResource.Builder cloningInstructions(
                CloningInstructions cloningInstructions) {
            this.cloningInstructions = cloningInstructions;
            return this;
        }

        public ControlledAiNotebookInstanceResource.Builder instanceName(String instanceName) {
            this.instanceName = instanceName;
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
                    instanceName);
        }
    }
}
