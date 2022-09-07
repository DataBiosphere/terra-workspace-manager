package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * ControlledResourceFields is used as a way to collect common resources for a controlled resource.
 * That way, we can more easily add common resource parameters to all resources without visiting
 * each implementation. Although for safe serialization, we still have to visit each @JsonCreator
 * resource constructor and add the parameters.
 *
 * <p>This allows us to make controller code that processes the common parts of the API input and
 * uses the methods in this class to populate the builder.
 *
 * <p>See {@link WsmResource} for details on the meaning of the fields
 */
public class WsmResourceFields {
  private final UUID workspaceUuid;
  private final UUID resourceId;
  private final String name;
  @Nullable private final String description;
  private final CloningInstructions cloningInstructions;
  @Nullable private final List<ResourceLineageEntry> resourceLineage;
  private final ImmutableMap<String, String> properties;

  /** construct from database resource */
  public WsmResourceFields(DbResource dbResource) {
    workspaceUuid = dbResource.getWorkspaceId();
    resourceId = dbResource.getResourceId();
    name = dbResource.getName();
    description = dbResource.getDescription();
    cloningInstructions = dbResource.getCloningInstructions();
    resourceLineage = dbResource.getResourceLineage().orElse(null);
    properties = dbResource.getProperties();
  }

  protected WsmResourceFields(Builder<?> builder) {
    this.workspaceUuid = builder.workspaceUuid;
    this.resourceId = builder.resourceId;
    this.name = builder.name;
    this.description = builder.description;
    this.cloningInstructions = builder.cloningInstructions;
    this.resourceLineage = builder.resourceLineage;
    this.properties = builder.properties;
  }

  public static WsmResourceFields.Builder<?> builder() {
    return new WsmResourceFields.Builder<>();
  }

  public WsmResourceFields.Builder<?> toBuilder() {
    return builder()
        .workspaceUuid(getWorkspaceId())
        .resourceId(getResourceId())
        .name(getName())
        .description(getDescription())
        .cloningInstructions(getCloningInstructions())
        .resourceLineage(getResourceLineage())
        .properties(getProperties());
  }

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  @Nullable
  public List<ResourceLineageEntry> getResourceLineage() {
    return resourceLineage;
  }

  public @Nullable ImmutableMap<String, String> getProperties() {
    return properties;
  }

  public static class Builder<T extends Builder<T>> {
    private UUID workspaceUuid;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private @Nullable List<ResourceLineageEntry> resourceLineage;
    private @Nullable ImmutableMap<String, String> properties;

    public Builder() {}

    public WsmResourceFields build() {
      ResourceValidationUtils.checkFieldNonNull(workspaceUuid, "workspaceId");
      ResourceValidationUtils.checkFieldNonNull(resourceId, "resourceId");
      ResourceValidationUtils.checkFieldNonNull(name, "name");
      ResourceValidationUtils.checkFieldNonNull(cloningInstructions, "cloningInstructions");

      this.properties = Optional.ofNullable(properties).orElse(ImmutableMap.of());

      return new WsmResourceFields(this);
    }

    public T workspaceUuid(UUID workspaceUuid) {
      this.workspaceUuid = workspaceUuid;
      return (T) this;
    }

    public T resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return (T) this;
    }

    public T name(String name) {
      this.name = name;
      return (T) this;
    }

    public T description(String description) {
      this.description = description;
      return (T) this;
    }

    public T cloningInstructions(CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return (T) this;
    }

    public T resourceLineage(@Nullable List<ResourceLineageEntry> resourceLineage) {
      this.resourceLineage = resourceLineage;
      return (T) this;
    }

    public T properties(@Nullable Map<String, String> properties) {
      this.properties =
          Optional.ofNullable(properties).map(ImmutableMap::copyOf).orElse(ImmutableMap.of());
      return (T) this;
    }
  }
}
