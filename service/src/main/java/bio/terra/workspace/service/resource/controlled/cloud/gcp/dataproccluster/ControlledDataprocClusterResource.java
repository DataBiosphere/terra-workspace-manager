package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.cloudres.google.dataproc.ClusterName;
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
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterAttributes;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.GcpResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.GrantPetUsagePermissionStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.RetrieveNetworkNameStep;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
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

/** A {@link ControlledResource} for a Google Dataproc clusters */
public class ControlledDataprocClusterResource extends ControlledResource {
  private static final String RESOURCE_DESCRIPTOR = "ControlledDataprocCluster";
  private final String clusterId;
  private final String region;
  private final String projectId;

  @JsonCreator
  public ControlledDataprocClusterResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("clusterId") String clusterId,
      @JsonProperty("region") String region,
      @JsonProperty("projectId") String projectId) {
    super(resourceFields, controlledResourceFields);
    this.clusterId = clusterId;
    this.region = region;
    this.projectId = projectId;
    validate();
  }

  // Constructor for the builder
  private ControlledDataprocClusterResource(
      ControlledResourceFields common, String clusterId, String region, String projectId) {
    super(common);
    this.clusterId = clusterId;
    this.region = region;
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

  /** The user specified id of the dataproc cluster. */
  public String getClusterId() {
    return clusterId;
  }

  /** The Google Cloud Platform region of the dataproc cluster, e.g. "us-east1". */
  public String getRegion() {
    return region;
  }

  /** The GCP project id where the cluster is created */
  public String getProjectId() {
    return projectId;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.DATAPROC_CLUSTER;
  }

  /** {@inheritDoc} */
  @Override
  @JsonIgnore
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("clusterId", getClusterId()));
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

    RetryRule gcpRetryRule = RetryRules.cloud();

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
        new CreateDataprocClusterStep(
            flightBeanBag.getControlledResourceService(),
            this,
            petSaEmail,
            workspaceUserFacingId,
            flightBeanBag.getCrlService(),
            flightBeanBag.getCliConfiguration(),
            flightBeanBag.getVersionConfiguration()),
        gcpRetryRule);
    flight.addStep(
        new DataprocClusterCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        RetryRules.longSync());
  }

  /** {@inheritDoc} */
  @Override
  public List<DeleteControlledResourceStep> getDeleteSteps(
      FlightMap inputParameters, FlightBeanBag flightBeanBag) {
    return List.of(new DeleteDataprocClusterStep(this, flightBeanBag.getCrlService()));
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new ValidateDataprocClusterUpdateStep(this, flightBeanBag.getCrlService()),
        RetryRules.cloud());
    flight.addStep(
        new RetrieveDataprocClusterResourceAttributesStep(this, flightBeanBag.getCrlService()),
        RetryRules.cloud());
    flight.addStep(
        new UpdateDataprocClusterStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  public ClusterName toClusterName() {
    return toClusterName(getRegion());
  }

  public ClusterName toClusterName(String region) {
    return ClusterName.builder().projectId(getProjectId()).region(region).name(clusterId).build();
  }

  public ApiGcpDataprocClusterResource toApiResource() {
    return new ApiGcpDataprocClusterResource()
        .metadata(toApiMetadata())
        .attributes(toApiAttributes());
  }

  public ApiGcpDataprocClusterAttributes toApiAttributes() {
    return new ApiGcpDataprocClusterAttributes()
        .projectId(projectId)
        .region(getRegion())
        .clusterId(getClusterId());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledDataprocClusterAttributes(getClusterId(), getRegion(), getProjectId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.gcpDataprocCluster(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_DATAPROC_CLUSTER
        || getResourceFamily() != WsmResourceFamily.DATAPROC_CLUSTER
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP_DATAPROC_CLUSTER");
    }
    if (!getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      throw new BadRequestException(
          "Access scope must be private. Shared Dataproc clusters are not yet implemented.");
    }
    ResourceValidationUtils.checkStringNonEmpty(getClusterId(), "clusterId", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkStringNonEmpty(getRegion(), "region", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkStringNonEmpty(getProjectId(), "projectId", RESOURCE_DESCRIPTOR);
    GcpResourceValidationUtils.validateDataprocClusterId(getClusterId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledDataprocClusterResource resource)) return false;
    if (!super.equals(o)) return false;
    return Objects.equal(clusterId, resource.clusterId)
        && Objects.equal(region, resource.region)
        && Objects.equal(projectId, resource.projectId);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledDataprocClusterResource that = (ControlledDataprocClusterResource) o;

    return clusterId.equals(that.clusterId) && region.equals(that.region);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), clusterId, region, projectId);
  }

  /** Builder for {@link ControlledDataprocClusterResource}. */
  public static class Builder {
    private ControlledResourceFields common;
    private String clusterId;
    private String region;
    private String projectId;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder clusterId(String clusterId) {
      this.clusterId = clusterId;
      return this;
    }

    public Builder region(String region) {
      this.region = region;
      return this;
    }

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public ControlledDataprocClusterResource build() {
      return new ControlledDataprocClusterResource(common, clusterId, region, projectId);
    }
  }
}
