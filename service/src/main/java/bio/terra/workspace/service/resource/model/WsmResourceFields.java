package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * WsmResourceFields is used as a way to collect common resources for a controlled resource. That
 * way, we can more easily add common resource parameters to all resources without visiting each
 * implementation. Although for safe serialization, we still have to visit each @JsonCreator
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
  private final String createdByEmail;
  private final @Nullable OffsetDateTime createdDate;
  private final @Nullable String region;

  /** construct from database resource */
  public WsmResourceFields(DbResource dbResource) {
    workspaceUuid = dbResource.getWorkspaceId();
    resourceId = dbResource.getResourceId();
    name = dbResource.getName();
    description = dbResource.getDescription();
    cloningInstructions = dbResource.getCloningInstructions();
    resourceLineage = dbResource.getResourceLineage().orElse(null);
    properties = dbResource.getProperties();
    createdByEmail = dbResource.getCreatedByEmail();
    createdDate = dbResource.getCreatedDate();
    region = dbResource.getRegion();
  }

  protected WsmResourceFields(Builder<?> builder) {
    this.workspaceUuid = builder.workspaceUuid;
    this.resourceId = builder.resourceId;
    this.name = builder.name;
    this.description = builder.description;
    this.cloningInstructions = builder.cloningInstructions;
    this.resourceLineage = builder.resourceLineage;
    this.properties = builder.properties;
    this.createdByEmail = builder.createdByEmail;
    this.createdDate = builder.createdDate;
    this.region = builder.region;
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
        .properties(getProperties())
        .createdByEmail(getCreatedByEmail());
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

  public ImmutableMap<String, String> getProperties() {
    return properties;
  }

  public String getCreatedByEmail() {
    return createdByEmail;
  }

  public String getRegion() {
    return region;
  }

  public OffsetDateTime getCreatedDate() {
    return createdDate;
  }

  public static class Builder<T extends Builder<T>> {
    private UUID workspaceUuid;
    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private List<ResourceLineageEntry> resourceLineage;
    private ImmutableMap<String, String> properties = ImmutableMap.of();
    private String createdByEmail;
    private OffsetDateTime createdDate;
    private String region;

    public Builder() {}

    public void validate() {
      ResourceValidationUtils.checkFieldNonNull(workspaceUuid, "workspaceId");
      ResourceValidationUtils.checkFieldNonNull(resourceId, "resourceId");
      ResourceValidationUtils.checkFieldNonNull(name, "name");
      ResourceValidationUtils.checkFieldNonNull(cloningInstructions, "cloningInstructions");
      ResourceValidationUtils.checkFieldNonNull(properties, "properties");
      ResourceValidationUtils.checkFieldNonNull(createdByEmail, "createdByEmail");
    }

    public WsmResourceFields build() {
      validate();
      return new WsmResourceFields(this);
    }

    @SuppressWarnings("unchecked")
    public T workspaceUuid(UUID workspaceUuid) {
      this.workspaceUuid = workspaceUuid;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T name(String name) {
      this.name = name;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T description(String description) {
      this.description = description;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T cloningInstructions(CloningInstructions cloningInstructions) {
      this.cloningInstructions = cloningInstructions;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T resourceLineage(@Nullable List<ResourceLineageEntry> resourceLineage) {
      this.resourceLineage = resourceLineage;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T properties(Map<String, String> properties) {
      this.properties = ImmutableMap.copyOf(properties);
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T createdByEmail(String createdByEmail) {
      this.createdByEmail = createdByEmail;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T createdDate(OffsetDateTime createdDate) {
      this.createdDate = createdDate;
      return (T) this;
    }
  }
}
