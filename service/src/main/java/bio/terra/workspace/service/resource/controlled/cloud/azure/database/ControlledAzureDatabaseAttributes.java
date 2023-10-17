package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(
    ignoreUnknown = true) // time was region was unnecessarily included in the json
public class ControlledAzureDatabaseAttributes {
  private final String databaseName;
  private final String databaseOwner;
  private final boolean allowAccessForAllWorkspaceUsers;

  @JsonCreator
  public ControlledAzureDatabaseAttributes(
      @JsonProperty("databaseName") String databaseName,
      @JsonProperty("databaseOwner") String databaseOwner,
      @JsonProperty("allowAccessForAllWorkspaceUsers") boolean allowAccessForAllWorkspaceUsers) {
    this.databaseName = databaseName;
    this.databaseOwner = databaseOwner;
    this.allowAccessForAllWorkspaceUsers = allowAccessForAllWorkspaceUsers;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public String getDatabaseOwner() {
    return databaseOwner;
  }

  public boolean getAllowAccessForAllWorkspaceUsers() {
    return allowAccessForAllWorkspaceUsers;
  }
}
