package bio.terra.workspace.db.model;

import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.StewardshipType;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.controlled.ControlledAccessType;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

import java.util.Optional;
import java.util.UUID;

/**
 * This class is used to have a common structure to hold the database view of a resource.
 * It includes all possible fields for a reference or controlled resource and (currently)
 * maps one-to-one with the resource table.
 */
public class DbResource {
    private UUID workspaceId;
    private CloudPlatform cloudPlatform;
    private UUID resourceId;
    private String name;
    private String description;
    private StewardshipType stewardshipType;
    private WsmResourceType resourceType;
    private CloningInstructions cloningInstructions;
    private String attributes;
    // controlled resource fields
    private ControlledAccessType accessType;
    private UUID associatedApp;
    private String assignedUser;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public DbResource workspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    public CloudPlatform getCloudPlatform() {
        return cloudPlatform;
    }

    public DbResource cloudPlatform(CloudPlatform cloudPlatform) {
        this.cloudPlatform = cloudPlatform;
        return this;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public DbResource resourceId(UUID resourceId) {
        this.resourceId = resourceId;
        return this;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public DbResource name(String name) {
        this.name = name;
        return this;
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public DbResource description(String description) {
        this.description = description;
        return this;
    }

    public StewardshipType getStewardshipType() {
        return stewardshipType;
    }

    public DbResource stewardshipType(StewardshipType stewardshipType) {
        this.stewardshipType = stewardshipType;
        return this;
    }

    public WsmResourceType getResourceType() {
        return resourceType;
    }

    public DbResource resourceType(WsmResourceType resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    public CloningInstructions getCloningInstructions() {
        return cloningInstructions;
    }

    public DbResource cloningInstructions(CloningInstructions cloningInstructions) {
        this.cloningInstructions = cloningInstructions;
        return this;
    }

    public String getAttributes() {
        return attributes;
    }

    public DbResource attributes(String attributes) {
        this.attributes = attributes;
        return this;
    }

    public Optional<ControlledAccessType> getAccessType() {
        return Optional.ofNullable(accessType);
    }

    public DbResource accessType(ControlledAccessType accessType) {
        this.accessType = accessType;
        return this;
    }

    public Optional<UUID> getAssociatedApp() {
        return Optional.ofNullable(associatedApp);
    }

    public DbResource associatedApp(UUID associatedApp) {
        this.associatedApp = associatedApp;
        return this;
    }

    public Optional<String> getAssignedUser() {
        return Optional.ofNullable(assignedUser);
    }

    public DbResource assignedUser(String assignedUser) {
        this.assignedUser = assignedUser;
        return this;
    }

}
