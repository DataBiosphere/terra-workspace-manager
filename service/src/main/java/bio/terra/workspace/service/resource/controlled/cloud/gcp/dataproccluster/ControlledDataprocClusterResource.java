package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
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
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourcesFlight;
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
import java.util.Optional;
import java.util.Set;

/** A {@link ControlledResource} for a Google Dataproc clusters */
public class ControlledDataprocClusterResource extends ControlledResource {

  /** The Dataproc cluster metadata key used to set the terra workspace. */
  protected static final String WORKSPACE_ID_METADATA_KEY = "terra-workspace-id";
  /**
   * The VM instance metadata key used to point the terra CLI at the correct WSM and SAM instances
   * given a CLI specific name.
   */
  protected static final String SERVER_ID_METADATA_KEY = "terra-cli-server";
  /**
   * When notebook has a custom image, disable root access and requires user to log in as Jupyter.
   * <a
   * href="https://github.com/hashicorp/terraform-provider-google/issues/7900#issuecomment-1067097275">...</a>.
   */
  protected static final String NOTEBOOK_DISABLE_ROOT_METADATA_KEY = "notebook-disable-root";

  private static final String RESOURCE_DESCRIPTOR = "ControlledDataprocCluster";

  /** Metadata keys that are reserved by terra. User cannot modify those. */
  public static final Set<String> RESERVED_METADATA_KEYS =
      Set.of(WORKSPACE_ID_METADATA_KEY, SERVER_ID_METADATA_KEY);

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

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
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

    RetryRule longSyncRetryRule = RetryRules.longSync();
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
            this,
            petSaEmail,
            workspaceUserFacingId,
            flightBeanBag.getCrlService(),
            flightBeanBag.getCliConfiguration()),
        gcpRetryRule);
    flight.addStep(
        new DataprocClusterCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        longSyncRetryRule);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteDataprocClusterStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    // TODO: Add update steps for setting autoscaling policy and manager node metadata. Change for
    // updating metadata in CRL needed.
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
    if (!(o instanceof ControlledDataprocClusterResource)) return false;
    if (!super.equals(o)) return false;
    ControlledDataprocClusterResource resource = (ControlledDataprocClusterResource) o;
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