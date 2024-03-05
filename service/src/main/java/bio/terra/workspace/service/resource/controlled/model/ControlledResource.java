package bio.terra.workspace.service.resource.controlled.model;

import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Class for all controlled resource fields that are not common to all resource stewardship types
 * and are not specific to any particular resource type.
 */
public abstract class ControlledResource extends WsmResource {
  WsmControlledResourceFields wsmControlledResourceFields;

  public ControlledResource(
      WsmResourceFields wsmResourceFields,
      @Nullable String assignedUser,
      AccessScopeType accessScope,
      ManagedByType managedBy,
      String applicationId,
      @Nullable PrivateResourceState privateResourceState,
      String region) {
    super(wsmResourceFields);
    this.wsmControlledResourceFields =
        new WsmControlledResourceFields(
            assignedUser, privateResourceState, accessScope, managedBy, applicationId, region);
  }

  /**
   * Construct the ControlledResource from database information
   *
   * @param dbResource POJO of the database info
   */
  public ControlledResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InvalidMetadataException("Expected CONTROLLED");
    }
    this.wsmControlledResourceFields = WsmControlledResourceFields.fromDb(dbResource);
  }

  /**
   * Construct the ControlledResource from the fields builder NOTE: we make a copy of the
   * WsmResourceFields. This seems prudent, so the created resource cannot be mutated by some input
   * field builder.
   *
   * @param fields container for building WsmResource and Controlled resource
   */
  protected ControlledResource(ControlledResourceFields fields) {
    super(
        WsmResourceFields.builder()
            .workspaceUuid(fields.getWorkspaceId())
            .resourceId(fields.getResourceId())
            .name(fields.getName())
            .description(fields.getDescription())
            .cloningInstructions(fields.getCloningInstructions())
            .resourceLineage(fields.getResourceLineage())
            .properties(fields.getProperties())
            .createdByEmail(fields.getCreatedByEmail())
            .createdDate(fields.getCreatedDate())
            .lastUpdatedByEmail(fields.getLastUpdatedByEmail())
            .lastUpdatedDate(fields.getLastUpdatedDate())
            .state(fields.getState())
            .flightId(fields.getFlightId())
            .error(fields.getError())
            .build());
    this.wsmControlledResourceFields = fields.getWsmControlledResourceFields();
  }

  public ControlledResource(
      WsmResourceFields resourceFields, WsmControlledResourceFields controlledResourceFields) {
    super(resourceFields);
    this.wsmControlledResourceFields = controlledResourceFields;
  }

  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return wsmControlledResourceFields;
  }

  /**
   * The ResourceDao calls this method for controlled parameters. The return value describes
   * filtering the DAO should do to verify the uniqueness of the resource. If the return is not
   * present, then no validation check will be performed.
   *
   * <p>JsonIgnore tells Jackson not to serialize this getter
   *
   * @return optional uniqueness description
   */
  @JsonIgnore
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
   * @param inputParameters the input parameters to the delete flight
   * @param flightBeanBag bean bag for finding Spring singletons
   */
  public abstract List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag);

  /**
   * The RemoveNativeAccessToPrivateResourcesFlight calls this method to populate the
   * resource-specific step(s) to remove native access to the specific cloud resource. This is only
   * required for private resources when resource specific access control methods are in use. It is
   * not usually required to override this method.
   *
   * <p>When overriding this method, also override getRestoreNativeAccessSteps.
   *
   * @param flightBeanBag bean bag for finding Spring singletons
   */
  public List<StepRetryRulePair> getRemoveNativeAccessSteps(FlightBeanBag flightBeanBag) {
    return List.of();
  }

  /**
   * The RestoreNativeAccessToPrivateResourcesFlight calls this method to populate the
   * resource-specific step(s) to restore native access to the specific cloud resource. This is only
   * required for private resources when resource specific access control methods are in use. It is
   * not usually required to override this method.
   *
   * <p>When overriding this method, also override getRemoveNativeAccessSteps.
   *
   * @param flightBeanBag bean bag for finding Spring singletons
   */
  public List<StepRetryRulePair> getRestoreNativeAccessSteps(FlightBeanBag flightBeanBag) {
    return List.of();
  }

  public <T extends ControlledResource> T getResourceFromFlightInputParameters(
      Flight flight, WsmResourceType resourceType) {
    return Preconditions.checkNotNull(
            flight
                .getInputParameters()
                .get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledResource.class))
        .castByEnum(resourceType);
  }

  /**
   * If specified, the assigned user must be equal to the user making the request.
   *
   * @return user email address for assignee, if any
   */
  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(wsmControlledResourceFields.assignedUser());
  }

  public AccessScopeType getAccessScope() {
    return wsmControlledResourceFields.accessScope();
  }

  public ManagedByType getManagedBy() {
    return wsmControlledResourceFields.managedBy();
  }

  public String getRegion() {
    return wsmControlledResourceFields.region();
  }

  public String getApplicationId() {
    return wsmControlledResourceFields.applicationId();
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return Optional.ofNullable(wsmControlledResourceFields.privateResourceState());
  }

  public ControlledResourceCategory getCategory() {
    return ControlledResourceCategory.get(
        wsmControlledResourceFields.accessScope(), wsmControlledResourceFields.managedBy());
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
            .accessScope(wsmControlledResourceFields.accessScope().toApiModel())
            .managedBy(wsmControlledResourceFields.managedBy().toApiModel())
            .privateResourceUser(
                // TODO: PF-616 figure out how to supply the assigned user's role
                new ApiPrivateResourceUser().userName(wsmControlledResourceFields.assignedUser()))
            .privateResourceState(
                getPrivateResourceState().map(PrivateResourceState::toApiModel).orElse(null))
            .region(getRegion());
    metadata.controlledResourceMetadata(controlled);
    return metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledResource that)) return false;
    if (!super.equals(o)) return false;
    return Objects.equal(wsmControlledResourceFields, that.wsmControlledResourceFields);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), wsmControlledResourceFields);
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
        && wsmControlledResourceFields.privateResourceState()
            != PrivateResourceState.NOT_APPLICABLE) {
      throw new InconsistentFieldsException(
          "Private resource state must be NOT_APPLICABLE for all non-private resources.");
    }
  }

  public ControlledResourceFields buildControlledCloneResourceCommonFields(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createByEmail,
      String region) {

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
                    : PrivateResourceState.NOT_APPLICABLE)
            .createdByEmail(createByEmail)
            .region(region);

    // override name and description if provided
    cloneResourceCommonFields.name(name == null ? getName() : name);
    cloneResourceCommonFields.description(description == null ? getDescription() : description);
    return cloneResourceCommonFields.build();
  }

  /**
   * Get the Sam action required on a workspace for a user to maintain access to a private resource
   * of this type. If a user loses this action on a workspace, they will lose access to all private
   * resources of this type in the workspace.
   *
   * @return
   */
  public String getRequiredSamActionForPrivateResource() {
    return SamWorkspaceAction.READ;
  }
}
