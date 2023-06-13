package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstants.DEFAULT_ZONE;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.UniquenessCheckAttributes;
import bio.terra.workspace.db.model.UniquenessCheckAttributes.UniquenessScope;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAttributes;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.GcpResourceValidationUtils;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** A {@link ControlledResource} for a Google AI Platform Notebook instance. */
public class ControlledAiNotebookInstanceResource extends ControlledResource {

  /** The Notebook instance metadata key used to control proxy mode. */
  protected static final String PROXY_MODE_METADATA_KEY = "proxy-mode";
  /** The Notebook instance metadata key used to set the terra workspace. */
  protected static final String WORKSPACE_ID_METADATA_KEY = "terra-workspace-id";
  /**
   * The Notebook instance metadata key used to point the terra CLI at the correct WSM and SAM
   * instances given a CLI specific name.
   */
  protected static final String SERVER_ID_METADATA_KEY = "terra-cli-server";
  /**
   * When notebook has a custom image, disable root access and requires user to log in as Jupyter.
   * <a
   * href="https://github.com/hashicorp/terraform-provider-google/issues/7900#issuecomment-1067097275">...</a>.
   */
  protected static final String NOTEBOOK_DISABLE_ROOT_METADATA_KEY = "notebook-disable-root";

  private static final String RESOURCE_DESCRIPTOR = "ControlledAiNotebookInstance";

  /** Metadata keys that are reserved by terra. User cannot modify those. */
  public static final Set<String> RESERVED_METADATA_KEYS =
      Set.of(PROXY_MODE_METADATA_KEY, WORKSPACE_ID_METADATA_KEY, SERVER_ID_METADATA_KEY);

  private final String instanceId;
  private final String location;
  private final String projectId;

  @JsonCreator
  public ControlledAiNotebookInstanceResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("wsmControlledResourceFields")
          WsmControlledResourceFields controlledResourceFields,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("location") String location,
      @JsonProperty("projectId") String projectId) {
    super(resourceFields, controlledResourceFields);
    this.instanceId = instanceId;
    this.location = location;
    this.projectId = projectId;
    validate();
  }

  // Constructor for the builder
  private ControlledAiNotebookInstanceResource(
      ControlledResourceFields common, String instanceId, String location, String projectId) {
    super(common);
    this.instanceId = instanceId;
    this.location = location;
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

  /** The user specified id of the notebook instance. */
  public String getInstanceId() {
    return instanceId;
  }

  /** The Google Cloud Platform location of the notebook instance, e.g. "us-east1-b". */
  public String getLocation() {
    return location;
  }

  /** The GCP project id where the notebook is created */
  public String getProjectId() {
    return projectId;
  }

  // -- getters not included in serialization --

  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AI_NOTEBOOK_INSTANCE;
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

  /**
   * Special retry rule for the notebook permission sync. We are seeing very long propagation times
   * in environments with Domain Restricted Sharing. This rule tries not to penalize non-DRS
   * environments by running an initial phase of retries with the usual "cloud" interval of 10
   * seconds. Then after a minute it switches to a longer interval. Currently set to wait 8 more
   * minutes.
   */
  private static class LongSyncRetryRule implements RetryRule {
    private static final int SHORT_INTERVAL_COUNT = 6;
    private static final int SHORT_INTERVAL_SECONDS = 10;
    private static final int LONG_INTERVAL_COUNT = 16;
    private static final int LONG_INTERVAL_SECONDS = 30;
    private int shortIntervalCounter;
    private int longIntervalCounter;

    @Override
    public void initialize() {
      shortIntervalCounter = 0;
      longIntervalCounter = 0;
    }

    @Override
    public boolean retrySleep() throws InterruptedException {
      if (shortIntervalCounter < SHORT_INTERVAL_COUNT) {
        shortIntervalCounter++;
        TimeUnit.SECONDS.sleep(SHORT_INTERVAL_SECONDS);
        return true;
      }
      if (longIntervalCounter < LONG_INTERVAL_COUNT) {
        longIntervalCounter++;
        TimeUnit.SECONDS.sleep(LONG_INTERVAL_SECONDS);
        return true;
      }
      return false;
    }
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

    RetryRule longSyncRetryRule = new LongSyncRetryRule();
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
        new CreateAiNotebookInstanceStep(
            this,
            petSaEmail,
            workspaceUserFacingId,
            flightBeanBag.getCrlService(),
            flightBeanBag.getCliConfiguration(),
            flightBeanBag.getVersionConfiguration()),
        gcpRetryRule);
    flight.addStep(
        new NotebookCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        longSyncRetryRule);
    flight.addStep(
        new UpdateNotebookResourceLocationAttributesStep(this, flightBeanBag.getResourceDao()),
        shortDatabaseRetryRule);
    flight.addStep(
        new UpdateControlledResourceRegionStep(flightBeanBag.getResourceDao(), getResourceId()),
        shortDatabaseRetryRule);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourcesFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAiNotebookInstanceStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    ControlledAiNotebookInstanceResource aiNotebookResource =
        getResourceFromFlightInputParameters(
            flight, WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);

    // Retrieve existing attributes in case of undo later.
    RetryRule gcpRetry = RetryRules.cloud();
    flight.addStep(
        new RetrieveAiNotebookResourceAttributesStep(
            aiNotebookResource, flightBeanBag.getCrlService()),
        gcpRetry);

    // Update the AI notebook's attributes.
    flight.addStep(
        new UpdateAiNotebookAttributesStep(aiNotebookResource, flightBeanBag.getCrlService()),
        gcpRetry);
  }

  public InstanceName toInstanceName() {
    return toInstanceName(getLocation());
  }

  public InstanceName toInstanceName(String requestedLocation) {
    return InstanceName.builder()
        .projectId(getProjectId())
        .location(requestedLocation)
        .instanceId(instanceId)
        .build();
  }

  public ApiGcpAiNotebookInstanceResource toApiResource() {
    return new ApiGcpAiNotebookInstanceResource()
        .metadata(toApiMetadata())
        .attributes(toApiAttributes());
  }

  public ApiGcpAiNotebookInstanceAttributes toApiAttributes() {
    return new ApiGcpAiNotebookInstanceAttributes()
        .projectId(projectId)
        .location(getLocation())
        .instanceId(getInstanceId());
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAiNotebookInstanceAttributes(getInstanceId(), getLocation(), getProjectId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    ApiResourceAttributesUnion union = new ApiResourceAttributesUnion();
    union.gcpAiNotebookInstance(toApiAttributes());
    return union;
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE
        || getResourceFamily() != WsmResourceFamily.AI_NOTEBOOK_INSTANCE
        || getStewardshipType() != StewardshipType.CONTROLLED) {
      throw new InconsistentFieldsException("Expected controlled GCP AI_NOTEBOOK_INSTANCE");
    }
    if (!getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      throw new BadRequestException(
          "Access scope must be private. Shared AI Notebook instances are not yet implemented.");
    }
    ResourceValidationUtils.checkFieldNonNull(getInstanceId(), "instanceId", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getLocation(), "location", RESOURCE_DESCRIPTOR);
    ResourceValidationUtils.checkFieldNonNull(getProjectId(), "projectId", RESOURCE_DESCRIPTOR);
    GcpResourceValidationUtils.validateAiNotebookInstanceId(getInstanceId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ControlledAiNotebookInstanceResource)) return false;
    if (!super.equals(o)) return false;
    ControlledAiNotebookInstanceResource resource = (ControlledAiNotebookInstanceResource) o;
    return Objects.equal(instanceId, resource.instanceId)
        && Objects.equal(location, resource.location)
        && Objects.equal(projectId, resource.projectId);
  }

  @Override
  public boolean partialEqual(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.partialEqual(o)) return false;

    ControlledAiNotebookInstanceResource that = (ControlledAiNotebookInstanceResource) o;

    return instanceId.equals(that.instanceId) && location.equals(that.location);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), instanceId, location, projectId);
  }

  /** Builder for {@link ControlledAiNotebookInstanceResource}. */
  public static class Builder {
    private ControlledResourceFields common;
    private String instanceId;
    private String location;
    private String projectId;

    public Builder common(ControlledResourceFields common) {
      this.common = common;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder location(@Nullable String location) {
      this.location = Optional.ofNullable(location).orElse(DEFAULT_ZONE);
      return this;
    }

    public Builder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    public ControlledAiNotebookInstanceResource build() {
      return new ControlledAiNotebookInstanceResource(common, instanceId, location, projectId);
    }
  }
}
