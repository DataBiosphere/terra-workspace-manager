package bio.terra.landingzone.db.model;

import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import java.util.UUID;
import java.util.function.Supplier;

/** This class is used to have a common structure to hold the database view of a landing zone. */
public class DbLandingZone {
  private UUID LandingZoneUuid;
  private String resourceGroup;
  private String template;
  private String version;
  private String attributes;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public UUID getWorkspaceLandingZoneUuid() {
    return LandingZoneUuid;
  }

  public DbLandingZone workspaceLandingZoneId(UUID workspaceLandingZoneUuid) {
    this.LandingZoneUuid = workspaceLandingZoneUuid;
    return this;
  }

  public String getResourceGroup() {
    return resourceGroup;
  }

  public DbLandingZone resourceGroup(String resourceGroup) {
    this.resourceGroup = resourceGroup;
    return this;
  }

  public String getAttributes() {
    return attributes;
  }

  public DbLandingZone attributes(String attributes) {
    this.attributes = attributes;
    return this;
  }
}
