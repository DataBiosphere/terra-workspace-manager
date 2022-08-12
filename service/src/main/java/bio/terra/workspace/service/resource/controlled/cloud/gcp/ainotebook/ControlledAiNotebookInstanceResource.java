package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.GcpResourceConstant.DEFAULT_ZONE;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
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
import bio.terra.workspace.generated.model.ApiResourceUnion;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceFlight;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

  /** Metadata keys that are reserved by terra. User cannot modify those. */
  public static final Set<String> RESERVED_METADATA_KEYS =
      Set.of(PROXY_MODE_METADATA_KEY, WORKSPACE_ID_METADATA_KEY, SERVER_ID_METADATA_KEY);

  protected static final int MAX_INSTANCE_NAME_LENGTH = 63;
  protected static final String AUTO_NAME_DATE_FORMAT = "-yyyyMMdd-HHmmss";
  private final String instanceId;
  private final String location;
  private final String projectId;

  @JsonCreator
  public ControlledAiNotebookInstanceResource(
      @JsonProperty("workspaceId") UUID workspaceId,
      @JsonProperty("resourceId") UUID resourceId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("cloningInstructions") CloningInstructions cloningInstructions,
      @JsonProperty("assignedUser") String assignedUser,
      @JsonProperty("privateResourceState") PrivateResourceState privateResourceState,
      @JsonProperty("accessScope") AccessScopeType accessScope,
      @JsonProperty("managedBy") ManagedByType managedBy,
      @JsonProperty("application") String applicationId,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("location") String location,
      @JsonProperty("projectId") String projectId) {
    super(
        workspaceId,
        resourceId,
        name,
        description,
        cloningInstructions,
        assignedUser,
        accessScope,
        managedBy,
        applicationId,
        privateResourceState);
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

  /** {@inheritDoc} */
  @Override
  public Optional<UniquenessCheckAttributes> getUniquenessCheckAttributes() {
    return Optional.of(
        new UniquenessCheckAttributes()
            .uniquenessScope(UniquenessScope.WORKSPACE)
            .addParameter("instanceId", getInstanceId())
            .addParameter("location", getLocation()));
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
        new CreateAiNotebookInstanceStep(
            this,
            petSaEmail,
            workspaceUserFacingId,
            flightBeanBag.getCrlService(),
            flightBeanBag.getCliConfiguration()),
        gcpRetryRule);
    flight.addStep(
        new NotebookCloudSyncStep(
            flightBeanBag.getControlledResourceService(),
            flightBeanBag.getCrlService(),
            this,
            userRequest),
        gcpRetryRule);
  }

  /** {@inheritDoc} */
  @Override
  public void addDeleteSteps(DeleteControlledResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(
        new DeleteAiNotebookInstanceStep(this, flightBeanBag.getCrlService()), RetryRules.cloud());
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

  public InstanceName toInstanceName(String workspaceProjectId) {
    return InstanceName.builder()
        .projectId(workspaceProjectId)
        .location(getLocation())
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
  public WsmResourceType getResourceType() {
    return WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE;
  }

  @Override
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.AI_NOTEBOOK_INSTANCE;
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
  public ApiResourceUnion toApiResourceUnion() {
    ApiResourceUnion union = new ApiResourceUnion();
    union.gcpAiNotebookInstance(toApiResource());
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
    checkFieldNonNull(getInstanceId(), "instanceId");
    checkFieldNonNull(getLocation(), "location");
    checkFieldNonNull(getProjectId(), "projectId");
    ResourceValidationUtils.validateAiNotebookInstanceId(getInstanceId());
  }

  public static String generateInstanceId(String instanceId) {
    return instanceId;
  }

  private static <T> void checkFieldNonNull(@Nullable T fieldValue, String fieldName) {
    if (fieldValue == null) {
      throw new MissingRequiredFieldException(
          String.format("Missing required field '%s' for ControlledNotebookInstance.", fieldName));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ControlledAiNotebookInstanceResource that = (ControlledAiNotebookInstanceResource) o;

    return instanceId.equals(that.instanceId) && location.equals(that.location);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + instanceId.hashCode();
    result = 31 * result + location.hashCode();
    return result;
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
