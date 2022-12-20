package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import java.util.Optional;
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
public class ControlledResourceFields extends WsmResourceFields {
  @Nullable private final String assignedUser;
  // We hold the iamRole to simplify the controller flow. It is not retained in the
  // controlled object.
  @Nullable private final ControlledResourceIamRole iamRole;
  // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
  @Nullable private final PrivateResourceState privateResourceState;
  private final AccessScopeType accessScope;
  private final ManagedByType managedBy;
  @Nullable private final String applicationId;
  @Nullable private final String region;

  /** construct from database resource */
  public ControlledResourceFields(DbResource dbResource) {
    super(dbResource);
    assignedUser = dbResource.getAssignedUser().orElse(null);
    // This field is used on create, but not stored in the database.
    iamRole = null;
    privateResourceState = dbResource.getPrivateResourceState().orElse(null);
    accessScope = dbResource.getAccessScope();
    managedBy = dbResource.getManagedBy();
    applicationId = dbResource.getApplicationId().orElse(null);
    region = dbResource.getRegion();
  }

  // constructor for the builder
  private ControlledResourceFields(Builder builder) {
    super(builder);
    this.assignedUser = builder.assignedUser;
    this.iamRole = builder.iamRole;
    this.privateResourceState = builder.privateResourceState;
    this.accessScope = builder.accessScope;
    this.managedBy = builder.managedBy;
    this.applicationId = builder.applicationId;
    this.region = builder.region;
  }

  public static ControlledResourceFields.Builder builder() {
    return new Builder();
  }

  @Nullable
  public ControlledResourceIamRole getIamRole() {
    return iamRole;
  }

  @Nullable
  public String getAssignedUser() {
    return assignedUser;
  }

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
  public String getRegion() {
    return region;
  }

  public static class Builder extends WsmResourceFields.Builder<Builder> {

    @Nullable private String assignedUser;
    // We hold the iamRole to simplify the controller flow. It is not retained in the
    // controlled object.
    @Nullable private ControlledResourceIamRole iamRole;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    @Nullable private String applicationId;
    @Nullable private String region;

    public Builder() {}

    public ControlledResourceFields build() {
      validate();
      return new ControlledResourceFields(this);
    }

    @Override
    public void validate() {
      super.validate();
      ResourceValidationUtils.checkFieldNonNull(accessScope, "accessScope");
      ResourceValidationUtils.checkFieldNonNull(managedBy, "managedBy");
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

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public Builder applicationId(@Nullable String applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public Builder region(@Nullable String region) {
      this.region = region;
      return this;
    }
  }
}
