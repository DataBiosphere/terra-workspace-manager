package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class ControlledAzureDatabaseAttributes {
  private final String databaseName;
  private final UUID databaseOwner;
  private final String region;
  @JsonCreator
  public ControlledAzureDatabaseAttributes(
      @JsonProperty("databaseName") String databaseName, @JsonProperty("databaseOwner") UUID databaseOwner, @JsonProperty("region") String region) {
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.region = region;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public String getRegion() {
    return region;
  }

  public UUID getDatabaseOwner() {
    return databaseOwner;
  }
}
