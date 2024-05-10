package bio.terra.workspace.db.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** This class is used to have a common structure to hold the database view of a cloud context. */
public class DbCloudContext implements DbStateful {
  private UUID workspaceUuid;
  private CloudPlatform cloudPlatform;
  private SpendProfileId spendProfile;
  private String contextJson;
  private WsmResourceState state;
  private String flightId;
  private ErrorReportException error;

  @Override
  public String makeSqlRowPredicate(MapSqlParameterSource params) {
    params.addValue("workspace_id", getWorkspaceId().toString());
    return "workspace_id = :workspace_id";
  }

  @Override
  public String getObjectTypeString() {
    return "cloud context";
  }

  @Override
  public String getObjectId() {
    return getWorkspaceId().toString();
  }

  @Override
  public String getTableName() {
    return "cloud_context";
  }

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public DbCloudContext workspaceUuid(UUID workspaceUuid) {
    this.workspaceUuid = workspaceUuid;
    return this;
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public DbCloudContext cloudPlatform(CloudPlatform cloudPlatform) {
    this.cloudPlatform = cloudPlatform;
    return this;
  }

  public String getContextJson() {
    return contextJson;
  }

  public DbCloudContext contextJson(String contextJson) {
    this.contextJson = contextJson;
    return this;
  }

  public SpendProfileId getSpendProfile() {
    return spendProfile;
  }

  public DbCloudContext spendProfile(SpendProfileId spendProfile) {
    this.spendProfile = spendProfile;
    return this;
  }

  @Override
  public WsmResourceState getState() {
    return state;
  }

  public DbCloudContext state(WsmResourceState state) {
    this.state = state;
    return this;
  }

  @Override
  public String getFlightId() {
    return flightId;
  }

  public DbCloudContext flightId(String flightId) {
    this.flightId = flightId;
    return this;
  }

  public ErrorReportException getError() {
    return error;
  }

  public DbCloudContext error(ErrorReportException error) {
    this.error = error;
    return this;
  }
}
