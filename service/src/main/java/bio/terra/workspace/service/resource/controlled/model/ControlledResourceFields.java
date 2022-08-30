package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * ControlledResourceFields is used as a way to collect common resources for a controlled resource.
 * That way, we can more easily add common resource parameters to all resources without visiting
 * each implementation. Although for safe serialization, we still have to visit each @JsonCreator
 * resource constructor and add the parameters.
 *
 * <p>This allows us to make controller code that processes the common parts of the API input and
 * uses the methods in this class to populate the builder.
 *
 * <p>See {@link ControlledResource} for details on the meaning of the fields
 */
public class ControlledResourceFields {
  private final UUID workspaceUuid;
  private final UUID resourceId;
  private final String name;
  @Nullable private final String description;
  private final CloningInstructions cloningInstructions;
  @Nullable private final String assignedUser;
  // We hold the iamRole to simplify the controller flow. It is not retained in the
  // controlled object.
  @Nullable private final ControlledResourceIamRole iamRole;
  // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
  @Nullable private final PrivateResourceState privateResourceState;
  private final AccessScopeType accessScope;
  private final ManagedByType managedBy;
  @Nullable private final String applicationId;
  @Nullable private final List<ResourceLineageEntry> resourceLineage;

  /** construct from database resource */
  public ControlledResourceFields(DbResource dbResource) {
    workspaceUuid = dbResource.getWorkspaceId();
    resourceId = dbResource.getResourceId();
    name = dbResource.getName();
    description = dbResource.getDescription();
    cloningInstructions = dbResource.getCloningInstructions();
    assignedUser = dbResource.getAssignedUser().orElse(null);
    // This field is used on create, but not stored in the database.
    iamRole = null;
    privateResourceState = dbResource.getPrivateResourceState().orElse(null);
    accessScope = dbResource.getAccessScope();
    managedBy = dbResource.getManagedBy();
    applicationId = dbResource.getApplicationId().orElse(null);
    resourceLineage = dbResource.getResourceLineage().orElse(null);
  }

  // constructor for the builder
  private ControlledResourceFields(
      UUID workspaceUuid,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions,
      @Nullable String assignedUser,
      @Nullable ControlledResourceIamRole iamRole,
      @Nullable PrivateResourceState privateResourceState,
      AccessScopeType accessScope,
      ManagedByType managedBy,
      @Nullable String applicationId,
      @Nullable List<ResourceLineageEntry> resourceLineage) {
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
    this.name = name;
    this.description = description;
    this.cloningInstructions = cloningInstructions;
    this.assignedUser = assignedUser;
    this.iamRole = iamRole;
    this.privateResourceState = privateResourceState;
    this.accessScope = accessScope;
    this.managedBy = managedBy;
    this.applicationId = applicationId;
    this.resourceLineage = resourceLineage;
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  @Nullable
  public ControlledResourceIamRole getIamRole() {
    return iamRole;
  }

  @Nullable
  public String getAssignedUser() {
    return assignedUser;
  }

  @Nullable
  public PrivateResourceState getPrivateResourceState() {
    return Optional.ofNullable(privateResourceState)
        .orElseGet(
            () ->
                this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
                    ? PrivateResourceState.INITIALIZING
                    : PrivateResourceState.NOT_APPLICABLE);
  }

  public AccessScopeType getAccessScope() {
    return accessScope;
  }

  public ManagedByType getManagedBy() {
    return managedBy;
  }

  @Nullable
  public String getApplicationId() {
    return applicationId;
  }

  @Nullable
  public List<ResourceLineageEntry> getResourceLineage() {
    return resourceLineage;
  }

  public static class Builder {
    private UUID workspaceUuid;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    @Nullable private String assignedUser;
    // We hold the iamRole to simplify the controller flow. It is not retained in the
    // controlled object.
    @Nullable private ControlledResourceIamRole iamRole;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    @Nullable private String applicationId;
    @Nullable private List<ResourceLineageEntry> resourceLineage;

    public ControlledResourceFields build() {
      ResourceValidationUtils.checkFieldNonNull(workspaceUuid, "workspaceId");
      ResourceValidationUtils.checkFieldNonNull(resourceId, "resourceId");
      ResourceValidationUtils.checkFieldNonNull(name, "name");
      ResourceValidationUtils.checkFieldNonNull(cloningInstructions, "cloningInstructions");
      ResourceValidationUtils.checkFieldNonNull(accessScope, "accessScope");
      ResourceValidationUtils.checkFieldNonNull(managedBy, "managedBy");

      return new ControlledResourceFields(
          workspaceUuid,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          iamRole,
          privateResourceState,
          accessScope,
          managedBy,
          applicationId,
          resourceLineage);
    }

    public Builder workspaceUuid(UUID workspaceUuid) {
      this.workspaceUuid = workspaceUuid;
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

    public Builder assignedUser(@Nullable String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public Builder iamRole(@Nullable ControlledResourceIamRole iamRole) {
      this.iamRole = iamRole;
      return this;
    }

    public Builder privateResourceState(@Nullable PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    public Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public ManagedByType getManagedBy() {
      return managedBy;
    }

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public Builder applicationId(@Nullable String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public Builder resourceLineage(@Nullable List<ResourceLineageEntry> resourceLineage) {
      this.resourceLineage = resourceLineage;
      return this;
    }
  }
}
