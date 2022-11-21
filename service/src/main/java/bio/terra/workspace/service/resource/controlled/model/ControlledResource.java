package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  @Nullable private final String assignedUser;
  private final AccessScopeType accessScope;
  @Nullable private final PrivateResourceState privateResourceState;
  private final ManagedByType managedBy;
  private final String applicationId;

  public ControlledResource(
      UUID workspaceUuid,
      UUID resourceId,
      String name,
      String description,
      CloningInstructions cloningInstructions,
      String assignedUser,
      AccessScopeType accessScope,
      ManagedByType managedBy,
      String applicationId,
      PrivateResourceState privateResourceState,
      List<ResourceLineageEntry> resourceLineage,
      Map<String, String> properties) {
    super(
        workspaceUuid,
        resourceId,
        name,
        description,
        cloningInstructions,
        resourceLineage,
        properties);
    this.assignedUser = assignedUser;
    this.accessScope = accessScope;
    this.managedBy = managedBy;
    this.applicationId = applicationId;
    this.privateResourceState = privateResourceState;
  }

  public ControlledResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Expected CONTROLLED");
    }
    this.assignedUser = dbResource.getAssignedUser().orElse(null);
    this.accessScope = dbResource.getAccessScope();
    this.managedBy = dbResource.getManagedBy();
    this.applicationId = dbResource.getApplicationId().orElse(null);
    this.privateResourceState = dbResource.getPrivateResourceState().orElse(null);
  }

  public ControlledResource(ControlledResourceFields builder) {
    super(
        builder.getWorkspaceId(),
        builder.getResourceId(),
        builder.getName(),
        builder.getDescription(),
        builder.getCloningInstructions(),
        builder.getResourceLineage(),
        builder.getProperties());
    this.assignedUser = builder.getAssignedUser();
    this.accessScope = builder.getAccessScope();
    this.managedBy = builder.getManagedBy();
    this.applicationId = builder.getApplicationId();
    this.privateResourceState = builder.getPrivateResourceState();
  }

  /**
   * The ResourceDao calls this method for controlled parameters. The return value describes
   * filtering the DAO should do to verify the uniqueness of the resource. If the return is not
   * present, then no validation check will be performed.
   *
   * @return optional uniqueness description
   */
  public abstract Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes();

  /**
   * The CreateControlledResourceFlight calls this method to populate the resource-specific steps to
   * create the specific cloud resource.
   *
   * @param flight the create flight
   * @param petSaEmail the pet SA to use for creation
   * @param userRequest authenticated user
   * @param flightBeanBag bean bag for finding Spring singletons
   */
  public abstract void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag);

  /**
   * The DeleteControlledResourceFlight calls this method to populate the resource-specific step(s)
   * to delete the specific cloud resource.
   *
   * @param flight the delete flight
   * @param flightBeanBag bean bag for finding Spring singletons
   */
  public abstract void addDeleteSteps(
      DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag);

  /**
   * If specified, the assigned user must be equal to the user making the request.
   *
   * @return user email address for assignee, if any
   */
  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(assignedUser);
  }

  public AccessScopeType getAccessScope() {
    return accessScope;
  }

  public ManagedByType getManagedBy() {
    return managedBy;
  }

  public String getApplicationId() {
    return applicationId;
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return Optional.ofNullable(privateResourceState);
  }

  public ControlledResourceCategory getCategory() {
    return ControlledResourceCategory.get(accessScope, managedBy);
  }

  @Override
  public StewardshipType getStewardshipType() {
    return StewardshipType.CONTROLLED;
  }

  @Override
  public ApiResourceMetadata toApiMetadata() {
    ApiResourceMetadata metadata = super.toApiMetadata();
    var controlled =
        new ApiControlledResourceMetadata()
            .accessScope(accessScope.toApiModel())
            .managedBy(managedBy.toApiModel())
            .privateResourceUser(
                // TODO: PF-616 figure out how to supply the assigned user's role
                new ApiPrivateResourceUser().userName(assignedUser))
            .privateResourceState(
                getPrivateResourceState().map(PrivateResourceState::toApiModel).orElse(null));
    metadata.controlledResourceMetadata(controlled);
    return metadata;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() == null
        || attributesToJson() == null
        || getAccessScope() == null
        || getManagedBy() == null) {
      throw new MissingRequiredFieldException("Missing required field for ControlledResource.");
    }
    if (getAssignedUser().isPresent() && getAccessScope() == AccessScopeType.ACCESS_SCOPE_SHARED) {
      throw new InconsistentFieldsException("Assigned user on SHARED resource");
    }

    if (getApplicationId() != null && getManagedBy() != ManagedByType.MANAGED_BY_APPLICATION) {
      throw new InconsistentFieldsException(
          "Application managed resource without an application id");
    }

    // Non-private resources must have NOT_APPLICABLE private resource state. Private resources can
    // have any of the private resource states, including NOT_APPLICABLE.
    if (getAccessScope() != AccessScopeType.ACCESS_SCOPE_PRIVATE
        && privateResourceState != PrivateResourceState.NOT_APPLICABLE) {
      throw new InconsistentFieldsException(
          "Private resource state must be NOT_APPLICABLE for all non-private resources.");
    }
  }

  public ControlledResourceFields buildControlledCloneResourceCommonFields(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description) {

    var cloneResourceCommonFields =
        ControlledResourceFields.builder()
            .accessScope(getAccessScope())
            .assignedUser(getAssignedUser().orElse(null))
            .cloningInstructions(getCloningInstructions())
            .managedBy(getManagedBy())
            .workspaceUuid(destinationWorkspaceUuid)
            .resourceId(destinationResourceId)
            .resourceLineage(buildCloneResourceLineage())
            .properties(buildCloneProperties(destinationWorkspaceUuid, destinationFolderId))
            .privateResourceState(
                getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE
                    ? PrivateResourceState.INITIALIZING
                    : PrivateResourceState.NOT_APPLICABLE);

    // override name and description if provided
    if (name != null) {
      cloneResourceCommonFields.name(name);
    }
    if (description != null) {
      cloneResourceCommonFields.description(description);
    }
    return cloneResourceCommonFields.build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledResource that = (ControlledResource) o;

    if (!Objects.equals(assignedUser, that.assignedUser)) return false;
    if (accessScope != that.accessScope) return false;
    return managedBy == that.managedBy;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (assignedUser != null ? assignedUser.hashCode() : 0);
    result = 31 * result + (accessScope != null ? accessScope.hashCode() : 0);
    result = 31 * result + (managedBy != null ? managedBy.hashCode() : 0);
    return result;
  }
}
