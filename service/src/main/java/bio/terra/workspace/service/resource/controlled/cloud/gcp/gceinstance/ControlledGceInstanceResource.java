package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.DEFAULT_ZONE;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceAttributes;
import bio.terra.workspace.generated.model.ApiGcpGceInstanceResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.GcpResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.UpdateInstanceResourceLocationAttributesStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceRegionStep;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.WsmControlledResourceFields;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** A {@link ControlledResource} for a Google Compute Engine VM instance. */
public class ControlledGceInstanceResource extends ControlledResource {
  private static final String RESOURCE_DESCRIPTOR = "ControlledGceInstance";

  /** Metadata keys that are reserved by terra. User cannot modify those. */
  public static final Set<String> RESERVED_METADATA_KEYS =
      Set.of(
          GcpResourceConstants.WORKSPACE_ID_METADATA_KEY,
          GcpResourceConstants.SERVER_ID_METADATA_KEY);

  private final String instanceId;
  private final String zone;
  private final String projectId;

  @JsonCreator
  public ControlledGceInstanceResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("zone") String zone,
      @JsonProperty("projectId") String projectId) {
    super(resourceFields, controlledResourceFields);
    this.instanceId = instanceId;
    this.zone = zone;
    this.projectId = projectId;
    validate();
  }

  // Constructor for the builder
  private ControlledGceInstanceResource(
      ControlledResourceFields common, String instanceId, String zone, String projectId) {
    super(common);
    this.instanceId = instanceId;
    this.zone = zone;
    this.projectId = projectId;
    validate();
  }

  public static Builder builder() {
    return new Builder();
  }

  // -- getters used in serialization --
  @Override
  @JsonProperty("wsmResourceFields")
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  @Override
  @JsonProperty("wsmControlledResourceFields")
  public WsmControlledResourceFields getWsmControlledResourceFields() {
    return super.getWsmControlledResourceFields();
  }

  /** The GCP zone of the instance, e.g. "us-east1-b". */
  public String getZone() {
    return zone;
  }

  /** The user specified name of the instance. */
  public String getInstanceId() {
    return instanceId;
  }

  /** The GCP project id where the instance is created */
  public String getProjectId() {
    return projectId;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.GCE_INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("instanceId", getInstanceId()));
  }

  /** {@inheritDoc} */
  @Override
  public void addCreateSteps(
      CreateControlledResourceFlight flight,
      String petSaEmail,
      AuthenticatedUserRequest userRequest,
      FlightBeanBag flightBeanBag) {

    WorkspaceDao workspaceDao = flightBeanBag.getWorkspaceDao();
    String workspaceUserFacingId = workspaceDao.getWorkspace(getWorkspaceId()).getUserFacingId();

    RetryRule longSyncRetryRule = RetryRules.longSync();
    RetryRule gcpRetryRule = RetryRules.cloud();
    RetryRule shortDatabaseRetryRule = RetryRules.shortDatabase();
    flight.addStep(
        new RetrieveNetworkNameStep(
            flightBeanBag.getCrlService(), this, flightBeanBag.getGcpCloudContextService()),
        gcpRetryRule);
    flight.addStep(
        new GrantPetUsagePermissionStep(
            getWorkspaceId(),
            userRequest,
            flightBeanBag.getPetSaService(),
            flightBeanBag.getSamService()),
        gcpRetryRule);
    flight.addStep(
        new CreateGceInstanceStep(
            this,
            petSaEmail,
            workspaceUserFacingId,
            flightBeanBag.getCrlService(),
            flightBeanBag.getCliConfiguration(),
            flightBeanBag.getVersionConfiguration()),
        gcpRetryRule);
    flight.addStep(
        new GceInstanceCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        longSyncRetryRule);
    flight.addStep(
        new UpdateInstanceResourceLocationAttributesStep(this, flightBeanBag.getResourceDao()),
        shortDatabaseRetryRule);
    flight.addStep(
        new UpdateControlledResourceRegionStep(flightBeanBag.getResourceDao(), getResourceId()),
        shortDatabaseRetryRule);
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(new DeleteGceInstanceStep(this, flightBeanBag.getCrlService()));
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    ControlledGceInstanceResource vmResource =
        getResourceFromFlightInputParameters(flight, WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE);

    // Retrieve existing attributes in case of undo later.
    RetryRule gcpRetry = RetryRules.cloud();
    flight.addStep(
        new RetrieveGceInstanceResourceAttributesStep(vmResource, flightBeanBag.getCrlService()),
        gcpRetry);

    // Update the instance attributes.
    flight.addStep(
        new UpdateGceInstanceAttributesStep(vmResource, flightBeanBag.getCrlService()), gcpRetry);
  }

  public ApiGcpGceInstanceResource toApiResource() {
    return new ApiGcpGceInstanceResource().metadata(toApiMetadata()).attributes(toApiAttributes());
  }

  public ApiGcpGceInstanceAttributes toApiAttributes() {
    return new ApiGcpGceInstanceAttributes()
        .projectId(getProjectId())
        .zone(getZone())
        .instanceId(getInstanceId());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledGceInstanceAttributes(getProjectId(), getZone(), getInstanceId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.gcpGceInstance(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_GCE_INSTANCE
        || getResourceFamily() != WsmResourceFamily.GCE_INSTANCE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP GCE_INSTANCE");
    }
    if (!getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      throw new BadRequestException(
          "Access scope must be private. Shared GCE instances are not yet implemented.");
    }
    ResourceValidationUtils.checkFieldNonNull(getInstanceId(), "instanceId", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getZone(), "zone", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getProjectId(), "projectId", RESOURCE_DESCRIPTOR);
    GcpResourceValidationUtils.validateGceInstanceId(getInstanceId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledGceInstanceResource resource)) return false;
    if (!super.equals(o)) return false;
    return Objects.equal(instanceId, resource.instanceId)
        && Objects.equal(zone, resource.zone)
        && Objects.equal(projectId, resource.projectId);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledGceInstanceResource that = (ControlledGceInstanceResource) o;

    return instanceId.equals(that.instanceId) && zone.equals(that.zone);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), projectId, zone, instanceId);
  }

  /** Builder for {@link ControlledGceInstanceResource}. */
  public static class Builder {
    private ControlledResourceFields common;
    private String instanceId;
    private String zone;
    private String projectId;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder zone(@Nullable String zone) {
      this.zone = Optional.ofNullable(zone).orElse(DEFAULT_ZONE);
      return this;
    }

    public ControlledGceInstanceResource build() {
      return new ControlledGceInstanceResource(common, instanceId, zone, projectId);
    }
  }
}
