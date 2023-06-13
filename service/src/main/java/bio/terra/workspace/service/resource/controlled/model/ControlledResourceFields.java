package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

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
  private static final String RESOURCE_DESCRIPTIOR = "ControlledResourceFields";
  private final WsmControlledResourceFields wsmControlledResourceFields;
  // We hold the iamRole to simplify the controller flow. It is not retained in the
  // controlled object.
  @Nullable private final ControlledResourceIamRole iamRole;

  /** construct from database resource */
  public ControlledResourceFields(DbResource dbResource) {
    super(dbResource);
    this.wsmControlledResourceFields = WsmControlledResourceFields.fromDb(dbResource);
    // This field is used on create, but not stored in the database.
    this.iamRole = null;
  }

  /**
   * Special constructor for controlled resources that carry their own region. That duplicates the
   * region in the common resource field. Legal states are:
   *
   * <p>both are null
   *
   * <p>both are there and identical
   *
   * <p>one is null; we use the other one
   *
   * @param dbResource Resource as stored in the database
   * @param resourceRegion Region from resource attributes
   */
  public ControlledResourceFields(DbResource dbResource, String resourceRegion) {
    this(fixDbResourceRegion(dbResource, resourceRegion));
  }

  private static DbResource fixDbResourceRegion(DbResource dbResource, String resourceRegion) {
    if (StringUtils.equals(dbResource.getRegion(), resourceRegion)) {
      // Either one null or conflict
      return dbResource;
    }
    if (dbResource.getRegion() != null && resourceRegion == null) {
      return dbResource;
    }
    if (resourceRegion != null && dbResource.getRegion() == null) {
      dbResource.region(resourceRegion);
      return dbResource;
    }
    throw new InternalLogicException("Inconsistent region data");
  }

  // constructor for the builder
  private ControlledResourceFields(Builder builder) {
    super(builder);
    this.wsmControlledResourceFields =
        new WsmControlledResourceFields(
            builder.assignedUser,
            builder.privateResourceState,
            builder.accessScope,
            builder.managedBy,
            builder.applicationId,
            builder.region);
    this.iamRole = builder.iamRole;
  }

  public static ControlledResourceFields.Builder builder() {
    return new Builder();
  }

  public @Nullable ControlledResourceIamRole getIamRole() {
    return iamRole;
  }

  @JsonIgnore
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return wsmControlledResourceFields;
  }

  public @Nullable String getAssignedUser() {
    return wsmControlledResourceFields.assignedUser();
  }

  public PrivateResourceState getPrivateResourceState() {
    return Optional.ofNullable(wsmControlledResourceFields.privateResourceState())
        .orElseGet(
            () ->
                this.wsmControlledResourceFields.accessScope()
                        == AccessScopeType.ACCESS_SCOPE_PRIVATE
                    ? PrivateResourceState.INITIALIZING
                    : PrivateResourceState.NOT_APPLICABLE);
  }

  public AccessScopeType getAccessScope() {
    return wsmControlledResourceFields.accessScope();
  }

  public ManagedByType getManagedBy() {
    return wsmControlledResourceFields.managedBy();
  }

  public @Nullable String getApplicationId() {
    return wsmControlledResourceFields.applicationId();
  }

  public @Nullable String getRegion() {
    return wsmControlledResourceFields.region();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledResourceFields that)) return false;
    if (!super.equals(o)) return false;
    return Objects.equal(wsmControlledResourceFields, that.wsmControlledResourceFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), wsmControlledResourceFields);
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
      computePrivateResourceState();
      validate();
      return new ControlledResourceFields(this);
    }

    @Override
    public void validate() {
      super.validate();
      ResourceValidationUtils.checkFieldNonNull(accessScope, "accessScope", RESOURCE_DESCRIPTIOR);
      ResourceValidationUtils.checkFieldNonNull(managedBy, "managedBy", RESOURCE_DESCRIPTIOR);
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

    /**
     * We allow privateResourceState to be left null in the builder. When we build, we compute a
     * default value based on the accessScope.
     */
    private void computePrivateResourceState() {
      if (privateResourceState != null) {
        return;
      }
      ResourceValidationUtils.checkFieldNonNull(accessScope, "accessScope", RESOURCE_DESCRIPTIOR);
      privateResourceState =
          (accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
              ? PrivateResourceState.INITIALIZING
              : PrivateResourceState.NOT_APPLICABLE);
    }
  }
}
