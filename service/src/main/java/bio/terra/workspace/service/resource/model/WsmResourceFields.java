package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * WsmResourceFields is used as a way to collect common resources for a controlled resource. That
 * way, we can more easily add common resource parameters to all resources without visiting each
 * implementation.
 *
 * <p>This allows us to make controller code that processes the common parts of the API input and
 * uses the methods in this class to populate the builder.
 *
 * <p>See {@link WsmResource} for details on the meaning of the fields
 */
@JsonDeserialize(builder = WsmResourceFields.Builder.class)
public class WsmResourceFields {
  private final UUID workspaceUuid;
  private final UUID resourceId;
  private final String name;
  @Nullable private final String description;
  private final CloningInstructions cloningInstructions;
  private final List<ResourceLineageEntry> resourceLineage;
  private final ImmutableMap<String, String> properties;
  private final String createdByEmail;
  @Nullable private final OffsetDateTime createdDate;
  private final String lastUpdatedByEmail;
  @Nullable private final OffsetDateTime lastUpdatedDate;

  /** construct from database resource */
  public WsmResourceFields(DbResource dbResource) {
    workspaceUuid = dbResource.getWorkspaceId();
    resourceId = dbResource.getResourceId();
    name = dbResource.getName();
    description = dbResource.getDescription();
    cloningInstructions = dbResource.getCloningInstructions();
    resourceLineage = dbResource.getResourceLineage().orElse(new ArrayList<>());
    properties = dbResource.getProperties();
    createdByEmail = dbResource.getCreatedByEmail();
    createdDate = dbResource.getCreatedDate();
    lastUpdatedByEmail = dbResource.getLastUpdatedByEmail();
    lastUpdatedDate = dbResource.getLastUpdatedDate();
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
    this.lastUpdatedByEmail = builder.lastUpdatedByEmail;
    this.lastUpdatedDate = builder.lastUpdatedDate;
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

  public List<ResourceLineageEntry> getResourceLineage() {
    return resourceLineage;
  }

  public ImmutableMap<String, String> getProperties() {
    return properties;
  }

  public String getCreatedByEmail() {
    return createdByEmail;
  }

  public @Nullable OffsetDateTime getCreatedDate() {
    return createdDate;
  }

  public String getLastUpdatedByEmail() {
    return lastUpdatedByEmail;
  }

  public @Nullable OffsetDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  // TODO: PF-2513 We should not leave fields out of the equals method
  //  Make a specific method for a partially equals semantic.
  //  Missing compares:
  //  - resourceLineage
  //  - createdDate
  //  - lastUpdatedByEmail
  //  - lastUpdatedDate

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WsmResourceFields that)) return false;
    return Objects.equal(workspaceUuid, that.workspaceUuid)
        && Objects.equal(resourceId, that.resourceId)
        && Objects.equal(name, that.name)
        && Objects.equal(description, that.description)
        && cloningInstructions == that.cloningInstructions
        && Objects.equal(properties, that.properties)
        && Objects.equal(createdByEmail, that.createdByEmail);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        workspaceUuid,
        resourceId,
        name,
        description,
        cloningInstructions,
        resourceLineage,
        properties,
        createdByEmail,
        createdDate,
        lastUpdatedByEmail,
        lastUpdatedDate);
  }

  /**
   * WsmResourceField Builder class
   *
   * @param <T> allow over
   */
  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder<T extends Builder<T>> {
    @JsonProperty("workspaceId")
    private UUID workspaceUuid;

    private UUID resourceId;
    private String name;
    private String description;
    private CloningInstructions cloningInstructions;
    private List<ResourceLineageEntry> resourceLineage = new ArrayList<>();
    private ImmutableMap<String, String> properties = ImmutableMap.of();
    private String createdByEmail;
    private OffsetDateTime createdDate;
    private String lastUpdatedByEmail;
    private OffsetDateTime lastUpdatedDate;

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
      this.resourceLineage = (resourceLineage == null) ? new ArrayList<>() : resourceLineage;
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

    @SuppressWarnings("unchecked")
    public T lastUpdatedByEmail(String lastUpdatedByEmail) {
      this.lastUpdatedByEmail = lastUpdatedByEmail;
      return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T lastUpdatedDate(OffsetDateTime lastUpdatedDate) {
      this.lastUpdatedDate = lastUpdatedDate;
      return (T) this;
    }
  }
}
