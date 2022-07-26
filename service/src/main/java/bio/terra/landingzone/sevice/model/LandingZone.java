package bio.terra.landingzone.sevice.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Internal representation of a Landing Zone. */
@JsonDeserialize(builder = LandingZone.Builder.class)
public class LandingZone {
  private final UUID landingZoneId;
  private final String resourceGroupId;
  private final String description;
  private final String displayName;
  private final String template;
  private final String version;
  private final Map<String, String> properties;

  public LandingZone(
      UUID landingZoneId,
      String resourceGroupId,
      String template,
      String version,
      String displayName,
      String description,
      Map<String, String> properties) {
    this.landingZoneId = landingZoneId;
    this.resourceGroupId = resourceGroupId;
    this.template = template;
    this.version = version;
    this.displayName = displayName;
    this.description = description;
    this.properties = properties;
  }

  /** The globally unique identifier of this landing zone. */
  public UUID getLandingZoneId() {
    return landingZoneId;
  }

  public String getResourceGroupId() {
    return resourceGroupId;
  }

  public Optional<String> getTemplate() {
    return Optional.ofNullable(template);
  }

  public Optional<String> getVersion() {
    return Optional.ofNullable(version);
  }

  /** Optional display name for the landing zone. */
  public Optional<String> getDisplayName() {
    return Optional.ofNullable(displayName);
  }

  /** Optional description of the landing zone. */
  public Optional<String> getDescription() {
    return Optional.ofNullable(description);
  }

  /** Caller-specified set of key-value pairs */
  public Map<String, String> getProperties() {
    return properties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    LandingZone landingZone = (LandingZone) o;

    return new EqualsBuilder()
        .append(landingZoneId, landingZone.landingZoneId)
        .append(resourceGroupId, landingZone.resourceGroupId)
        .append(template, landingZone.template)
        .append(version, landingZone.version)
        .append(displayName, landingZone.displayName)
        .append(description, landingZone.description)
        .append(properties, landingZone.properties)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(landingZoneId)
        .append(resourceGroupId)
        .append(template)
        .append(version)
        .append(displayName)
        .append(description)
        .append(properties)
        .toHashCode();
  }

  public static Builder builder() {
    return new Builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class Builder {
    private UUID landingZoneId;
    private String resourceGroupId;
    private String template;
    private String version;
    private String displayName;
    private String description;
    private Map<String, String> properties;

    public Builder landingZoneId(UUID landingZoneUuid) {
      this.landingZoneId = landingZoneUuid;
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

    public Builder resourceGroupId(String resourceGroupId) {
      this.resourceGroupId = resourceGroupId;
      return this;
    }

    public Builder properties(Map<String, String> properties) {
      this.properties = properties;
      return this;
    }

    public Builder template(String template) {
      this.template = template;
      return this;
    }

    public Builder version(String version) {
      this.version = version;
      return this;
    }

    public LandingZone build() {
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
      if (landingZoneId == null) {
        throw new MissingRequiredFieldsException("Landing zone requires id");
      }
      return new LandingZone(
          landingZoneId, resourceGroupId, template, version, displayName, description, properties);
    }
  }
}
