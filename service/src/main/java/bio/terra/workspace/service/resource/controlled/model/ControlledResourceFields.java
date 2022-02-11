package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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
 */
public class ControlledResourceFields {
  private UUID workspaceId;
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
  private UUID applicationId;

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public ControlledResourceFields workspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public ControlledResourceFields resourceId(UUID resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public String getName() {
    return name;
  }

  public ControlledResourceFields name(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public ControlledResourceFields description(String description) {
    this.description = description;
    return this;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public ControlledResourceFields cloningInstructions(CloningInstructions cloningInstructions) {
    this.cloningInstructions = cloningInstructions;
    return this;
  }

  @Nullable
  public ControlledResourceIamRole getIamRole() {
    return iamRole;
  }

  public ControlledResourceFields iamRole(@Nullable ControlledResourceIamRole iamRole) {
    this.iamRole = iamRole;
    return this;
  }

  @Nullable
  public String getAssignedUser() {
    return assignedUser;
  }

  public ControlledResourceFields assignedUser(@Nullable String assignedUser) {
    this.assignedUser = assignedUser;
    return this;
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

  public ControlledResourceFields privateResourceState(
      @Nullable PrivateResourceState privateResourceState) {
    this.privateResourceState = privateResourceState;
    return this;
  }

  public AccessScopeType getAccessScope() {
    return accessScope;
  }

  public ControlledResourceFields accessScope(AccessScopeType accessScope) {
    this.accessScope = accessScope;
    return this;
  }

  public ManagedByType getManagedBy() {
    return managedBy;
  }

  public ControlledResourceFields managedBy(ManagedByType managedBy) {
    this.managedBy = managedBy;
    return this;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public ControlledResourceFields applicationId(UUID applicationId) {
    this.applicationId = applicationId;
    return this;
  }
}
