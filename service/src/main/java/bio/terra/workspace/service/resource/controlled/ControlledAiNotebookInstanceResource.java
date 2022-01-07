package bio.terra.workspace.service.resource.controlled;

import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.InconsistentFieldsException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAttributes;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.ValidationUtils;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

/** A {@link ControlledResource} for a Google AI Platform Notebook instance. */
public class ControlledAiNotebookInstanceResource extends ControlledResource {

  protected static final int MAX_INSTANCE_NAME_LENGTH = 61;
  protected static final String AUTO_NAME_DATE_FORMAT = "-yyyyMMdd-HHmmss";
  private final String instanceId;
  private final String location;

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
      @JsonProperty("application") UUID applicationId,
      @JsonProperty("instanceId") String instanceId,
      @JsonProperty("location") String location) {
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
    validate();
  }

  public ControlledAiNotebookInstanceResource(DbResource dbResource) {
    super(dbResource);
    ControlledAiNotebookInstanceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledAiNotebookInstanceAttributes.class);
    this.instanceId = attributes.getInstanceId();
    this.location = attributes.getLocation();
    validate();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder()
        .workspaceId(getWorkspaceId())
        .resourceId(getResourceId())
        .name(getName())
        .description(getDescription())
        .cloningInstructions(getCloningInstructions())
        .assignedUser(getAssignedUser().orElse(null))
        .privateResourceState(getPrivateResourceState().orElse(null))
        .accessScope(getAccessScope())
        .managedBy(getManagedBy())
        .applicationId(getApplicationId())
        .instanceId(getInstanceId())
        .location(getLocation());
  }

  /** The user specified id of the notebook instance. */
  public String getInstanceId() {
    return instanceId;
  }

  /** The Google Cloud Platform location of the notebook instance, e.g. "us-east1-b". */
  public String getLocation() {
    return location;
  }

  public InstanceName toInstanceName(String workspaceProjectId) {
    return InstanceName.builder()
        .projectId(workspaceProjectId)
        .location(getLocation())
        .instanceId(instanceId)
        .build();
  }

  public ApiGcpAiNotebookInstanceResource toApiResource(String workspaceProjectId) {
    return new ApiGcpAiNotebookInstanceResource()
        .metadata(toApiMetadata())
        .attributes(toApiAttributes(workspaceProjectId));
  }

  public ApiGcpAiNotebookInstanceAttributes toApiAttributes(String workspaceProjectId) {
    return new ApiGcpAiNotebookInstanceAttributes()
        .projectId(workspaceProjectId)
        .location(getLocation())
        .instanceId(getInstanceId());
  }

  @Override
  public WsmResourceType getResourceType() {
    return WsmResourceType.AI_NOTEBOOK_INSTANCE;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ControlledAiNotebookInstanceAttributes(getInstanceId(), getLocation()));
  }

  @Override
  public void validate() {
    super.validate();
    if (getResourceType() != WsmResourceType.AI_NOTEBOOK_INSTANCE) {
      throw new InconsistentFieldsException("Expected AI_NOTEBOOK_INSTANCE");
    }
    if (!getAccessScope().equals(AccessScopeType.ACCESS_SCOPE_PRIVATE)) {
      throw new BadRequestException(
          "Access scope must be private. Shared AI Notebook instances are not yet implemented.");
    }
    checkFieldNonNull(getInstanceId(), "instanceId");
    checkFieldNonNull(getLocation(), "location");
    ValidationUtils.validateAiNotebookInstanceId(getInstanceId());
  }

  /** Returns an auto generated instance name with the username and date time. */
  public static String generateInstanceId(@Nullable String user) {
    String mangledUsername = mangleUsername(extractUsername(user));
    String localDateTimeSuffix =
        DateTimeFormatter.ofPattern(AUTO_NAME_DATE_FORMAT)
            .format(Instant.now().atZone(ZoneId.systemDefault()));
    return mangledUsername + localDateTimeSuffix;
  }

  /**
   * Best effort mangle the user's name so that it meets the requirements for a valid instance name.
   *
   * <p>Instance name id must match the regex '(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)', i.e. starting
   * with a lowercase alpha character, only alphanumerics and '-' of max length 61. I don't have a
   * documentation link, but gcloud will complain otherwise.
   */
  private static String mangleUsername(String username) {
    // Strip non alpha-numeric or '-' characters.
    String mangledName = username.replaceAll("[^a-zA-Z0-9-]", "");
    if (mangledName.isEmpty()) {
      mangledName = "notebook";
    }
    // Lower case everything, even though only the first character requires lowercase.
    mangledName = mangledName.toLowerCase();
    // Make sure the returned name isn't too long to not have the date time suffix.
    int maxNameLength = MAX_INSTANCE_NAME_LENGTH - AUTO_NAME_DATE_FORMAT.length();
    if (mangledName.length() > maxNameLength) {
      mangledName = mangledName.substring(0, maxNameLength);
    }
    return mangledName;
  }

  private static String extractUsername(@Nullable String validEmail) {
    if (StringUtils.isEmpty(validEmail)) {
      return "";
    }
    return validEmail.substring(0, validEmail.indexOf('@'));
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
    private UUID workspaceId;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private String assignedUser;
    // Default value is NOT_APPLICABLE for shared resources and INITIALIZING for private resources.
    @Nullable private PrivateResourceState privateResourceState;
    private AccessScopeType accessScope;
    private ManagedByType managedBy;
    private UUID applicationId;
    private String instanceId;
    private String location;

    public Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder cloningInstructions(CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return this;
    }

    public Builder instanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    public Builder location(String location) {
      this.location = location;
      return this;
    }

    public Builder assignedUser(String assignedUser) {
      this.assignedUser = assignedUser;
      return this;
    }

    public Builder privateResourceState(PrivateResourceState privateResourceState) {
      this.privateResourceState = privateResourceState;
      return this;
    }

    private PrivateResourceState defaultPrivateResourceState() {
      return this.accessScope == AccessScopeType.ACCESS_SCOPE_PRIVATE
          ? PrivateResourceState.INITIALIZING
          : PrivateResourceState.NOT_APPLICABLE;
    }

    public Builder accessScope(AccessScopeType accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public Builder managedBy(ManagedByType managedBy) {
      this.managedBy = managedBy;
      return this;
    }

    public Builder applicationId(UUID applicationId) {
      this.applicationId = applicationId;
      return this;
    }

    public ControlledAiNotebookInstanceResource build() {
      return new ControlledAiNotebookInstanceResource(
          workspaceId,
          resourceId,
          name,
          description,
          cloningInstructions,
          assignedUser,
          Optional.ofNullable(privateResourceState).orElse(defaultPrivateResourceState()),
          accessScope,
          managedBy,
          applicationId,
          instanceId,
          location);
    }
  }
}
