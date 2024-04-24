package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.db.model.DbWorkspace;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.WorkspaceConstants.Properties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Internal representation of a Workspace.
 *
 * <p>A workspace is a collection of resources, data references, and applications with some shared
 * context. In general, a workspace is the fundamental unit of analysis in Terra. Workspaces may
 * have an associated billing account and may have zero or one associated GCP projects.
 */
@JsonDeserialize(builder = Workspace.Builder.class)
public record Workspace(
    UUID workspaceId,
    String userFacingId,
    @Nullable String displayName,
    @Nullable String description,
    SpendProfileId spendProfileId,
    Map<String, String> properties,
    WorkspaceStage workspaceStage,
    String createdByEmail,
    OffsetDateTime createdDate,
    WsmResourceState state,
    String flightId,
    ErrorReportException error) {

  /** The globally unique identifier of this workspace */
  public UUID getWorkspaceId() {
    return workspaceId;
  }

  /** User facing id. Required. */
  public String getUserFacingId() {
    return userFacingId;
  }

  /** Optional display name for the workspace. */
  public Optional<String> getDisplayName() {
    return Optional.ofNullable(displayName);
  }

  /** Optional description of the workspace. */
  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  /**
   * The spend profile ID associated with this project, if one exists.
   *
   * <p>In the future, this will correlate to a spend profile in the Spend Profile Manager. For now,
   * it's just a unique identifier. To associate a GCP project with a workspace, the workspace must
   * have a spend profile. They are not needed otherwise.
   */
  public Optional<SpendProfileId> getSpendProfileId() {
    return Optional.ofNullable(spendProfileId);
  }

  /** Caller-specified set of key-value pairs */
  public Map<String, String> getProperties() {
    return properties;
  }

  /** Feature flag indicating whether this workspace uses MC Terra features. */
  public WorkspaceStage getWorkspaceStage() {
    return workspaceStage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    Workspace workspace = (Workspace) o;

    return new EqualsBuilder()
        .append(workspaceId, workspace.workspaceId)
        .append(userFacingId, workspace.userFacingId)
        .append(displayName, workspace.displayName)
        .append(description, workspace.description)
        .append(spendProfileId, workspace.spendProfileId)
        .append(properties, workspace.properties)
        .append(workspaceStage, workspace.workspaceStage)
        .append(createdByEmail, workspace.createdByEmail)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(workspaceId)
        .append(userFacingId)
        .append(displayName)
        .append(description)
        .append(spendProfileId)
        .append(properties)
        .append(workspaceStage)
        .toHashCode();
  }

  public static Builder builder() {
    return new Builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private UUID workspaceId;
    private String userFacingId;
    private @Nullable String displayName;
    private String description;
    private SpendProfileId spendProfileId;
    private Map<String, String> properties;
    private WorkspaceStage workspaceStage;
    private String createdByEmail;
    private @Nullable OffsetDateTime createdDate;
    private WsmResourceState state;
    private @Nullable String flightId;
    private @Nullable ErrorReportException error;

    public Builder workspaceId(UUID workspaceUuid) {
      this.workspaceId = workspaceUuid;
      return this;
    }

    public Builder userFacingId(String userFacingId) {
      this.userFacingId = userFacingId;
      return this;
    }

    public Builder displayName(@Nullable String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(@Nullable String description) {
      this.description = description;
      return this;
    }

    public Builder spendProfileId(SpendProfileId spendProfileId) {
      this.spendProfileId = spendProfileId;
      return this;
    }

    public Builder properties(Map<String, String> properties) {
      this.properties = properties;
      return this;
    }

    public Builder workspaceStage(WorkspaceStage workspaceStage) {
      this.workspaceStage = workspaceStage;
      return this;
    }

    public Builder createdByEmail(String email) {
      this.createdByEmail = email;
      return this;
    }

    public Builder createdDate(OffsetDateTime createdDate) {
      this.createdDate = createdDate;
      return this;
    }

    public Builder state(WsmResourceState state) {
      this.state = state;
      return this;
    }

    public Builder flightId(@Nullable String flightId) {
      this.flightId = flightId;
      return this;
    }

    public Builder error(@Nullable ErrorReportException error) {
      this.error = error;
      return this;
    }

    public Workspace build() {
      // Always have a map, even if it is empty
      if (properties == null) {
        properties = new HashMap<>();
      }
      if (workspaceId == null || workspaceStage == null) {
        throw new MissingRequiredFieldsException("Workspace requires id and stage");
      }
      if (createdByEmail == null) {
        throw new InternalServerErrorException(
            "Something went wrong, createdByEmail shouldn't be null");
      }
      return new Workspace(
          workspaceId,
          userFacingId,
          displayName,
          description,
          spendProfileId,
          properties,
          workspaceStage,
          createdByEmail,
          createdDate,
          state,
          flightId,
          error);
    }
  }

  /**
   * Make a workspace object from the database workspace object.
   *
   * @param dbWorkspace nullable database workspace object
   * @return Workspace or null if not found
   */
  public static Workspace makeFromDb(@Nullable DbWorkspace dbWorkspace) {
    return dbWorkspace == null
        ? null
        : Workspace.builder()
            .workspaceId(dbWorkspace.getWorkspaceId())
            .userFacingId(dbWorkspace.getUserFacingId())
            .displayName(dbWorkspace.getDisplayName())
            .description(dbWorkspace.getDescription())
            .spendProfileId(dbWorkspace.getSpendProfileId())
            .properties(dbWorkspace.getProperties())
            .workspaceStage(dbWorkspace.getWorkspaceStage())
            .createdByEmail(dbWorkspace.getCreatedByEmail())
            .createdDate(dbWorkspace.getCreatedDate())
            .state(dbWorkspace.getState())
            .flightId(dbWorkspace.getFlightId())
            .error(dbWorkspace.getError())
            .build();
  }

  /**
   * If requester only has discoverer role, they can only see a subset of workspace. They cannot
   * see:
   *
   * <ul>
   *   <li>Workspace description
   *   <li>Spend profile
   *   <li>Can see special type, short description and version properties, and no other properties
   * </ul>
   */
  public static Workspace stripWorkspaceForRequesterWithOnlyDiscovererRole(
      Workspace fullWorkspace) {
    Workspace.Builder strippedWorkspace =
        new Builder()
            .workspaceId(fullWorkspace.getWorkspaceId())
            .userFacingId(fullWorkspace.getUserFacingId())
            .workspaceStage(fullWorkspace.getWorkspaceStage())
            .displayName(fullWorkspace.getDisplayName().orElse(null))
            .createdByEmail(fullWorkspace.createdByEmail)
            .createdDate(fullWorkspace.createdDate);

    Map<String, String> strippedProperties = new HashMap<>();
    if (fullWorkspace.getProperties().containsKey(Properties.TYPE)) {
      strippedProperties.put(Properties.TYPE, fullWorkspace.getProperties().get(Properties.TYPE));
    }
    if (fullWorkspace.getProperties().containsKey(Properties.SHORT_DESCRIPTION)) {
      strippedProperties.put(
          Properties.SHORT_DESCRIPTION,
          fullWorkspace.getProperties().get(Properties.SHORT_DESCRIPTION));
    }
    if (fullWorkspace.getProperties().containsKey(Properties.VERSION)) {
      strippedProperties.put(
          Properties.VERSION, fullWorkspace.getProperties().get(Properties.VERSION));
    }
    strippedWorkspace.properties(strippedProperties);

    return strippedWorkspace.build();
  }
}
