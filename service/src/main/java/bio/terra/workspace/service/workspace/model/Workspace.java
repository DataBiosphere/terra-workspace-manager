package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
public class Workspace {
  private final UUID workspaceId;
  private final String displayName;
  private final String description;
  private final SpendProfileId spendProfileId;
  private final Map<String, String> properties;
  private final WorkspaceStage workspaceStage;

  public Workspace(
      UUID workspaceId,
      String displayName,
      String description,
      SpendProfileId spendProfileId,
      Map<String, String> properties,
      WorkspaceStage workspaceStage) {
    this.workspaceId = workspaceId;
    this.displayName = displayName;
    this.description = description;
    this.spendProfileId = spendProfileId;
    this.properties = properties;
    this.workspaceStage = workspaceStage;
  }

  /** The globally unique identifier of this workspace */
  public UUID getWorkspaceId() {
    return workspaceId;
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
        .append(displayName, workspace.displayName)
        .append(description, workspace.description)
        .append(spendProfileId, workspace.spendProfileId)
        .append(properties, workspace.properties)
        .append(workspaceStage, workspace.workspaceStage)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(workspaceId)
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
    private String displayName;
    private String description;
    private SpendProfileId spendProfileId;
    private Map<String, String> properties;
    private WorkspaceStage workspaceStage;

    public Builder workspaceId(UUID workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(String description) {
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

    public Workspace build() {
      // Always have a map, even if it is empty
      if (properties == null) {
        properties = new HashMap<>();
      }
      if (displayName == null) {
        displayName = "";
      }
      if (description == null) {
        description = "";
      }
      if (workspaceId == null || workspaceStage == null) {
        throw new MissingRequiredFieldsException("Workspace requires id and stage");
      }
      return new Workspace(
          workspaceId, displayName, description, spendProfileId, properties, workspaceStage);
    }
  }
}
