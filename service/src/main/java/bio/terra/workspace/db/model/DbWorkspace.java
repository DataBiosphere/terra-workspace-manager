package bio.terra.workspace.db.model;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.model.SpendProfileId;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import javax.annotation.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public class DbWorkspace implements DbStateful {
  private UUID workspaceId;
  private String userFacingId;
  private @Nullable String displayName;
  private @Nullable String description;
  private SpendProfileId spendProfileId;
  private Map<String, String> properties;
  private WorkspaceStage workspaceStage;
  private String createdByEmail;
  private OffsetDateTime createdDate;
  private WsmResourceState state;
  private String flightId;
  private ErrorReportException error;

  @Override
  public String getObjectTypeString() {
    return "workspace";
  }

  @Override
  public String getObjectId() {
    return getWorkspaceId().toString();
  }

  @Override
  public String getTableName() {
    return "workspace";
  }

  @Override
  public String makeSqlRowPredicate(MapSqlParameterSource params) {
    params.addValue("workspace_id", getWorkspaceId().toString());
    return "workspace_id = :workspace_id";
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public DbWorkspace workspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
    return this;
  }

  public String getUserFacingId() {
    return userFacingId;
  }

  public DbWorkspace userFacingId(String userFacingId) {
    this.userFacingId = userFacingId;
    return this;
  }

  @Nullable
  public String getDisplayName() {
    return displayName;
  }

  public DbWorkspace displayName(@Nullable String displayName) {
    this.displayName = displayName;
    return this;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public DbWorkspace description(@Nullable String description) {
    this.description = description;
    return this;
  }

  public SpendProfileId getSpendProfileId() {
    return spendProfileId;
  }

  public DbWorkspace spendProfileId(SpendProfileId spendProfileId) {
    this.spendProfileId = spendProfileId;
    return this;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public DbWorkspace properties(Map<String, String> properties) {
    this.properties = properties;
    return this;
  }

  public WorkspaceStage getWorkspaceStage() {
    return workspaceStage;
  }

  public DbWorkspace workspaceStage(WorkspaceStage workspaceStage) {
    this.workspaceStage = workspaceStage;
    return this;
  }

  public String getCreatedByEmail() {
    return createdByEmail;
  }

  public DbWorkspace createdByEmail(String createdByEmail) {
    this.createdByEmail = createdByEmail;
    return this;
  }

  public OffsetDateTime getCreatedDate() {
    return createdDate;
  }

  public DbWorkspace createdDate(OffsetDateTime createdDate) {
    this.createdDate = createdDate;
    return this;
  }

  @Override
  public WsmResourceState getState() {
    return state;
  }

  public DbWorkspace state(WsmResourceState state) {
    this.state = state;
    return this;
  }

  @Override
  public @Nullable String getFlightId() {
    return flightId;
  }

  public DbWorkspace flightId(String flightId) {
    this.flightId = flightId;
    return this;
  }

  public ErrorReportException getError() {
    return error;
  }

  public DbWorkspace error(ErrorReportException error) {
    this.error = error;
    return this;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", DbWorkspace.class.getSimpleName() + "[", "]")
        .add("workspaceId=" + workspaceId)
        .add("userFacingId='" + userFacingId + "'")
        .add("displayName='" + displayName + "'")
        .add("description='" + description + "'")
        .add("spendProfileId=" + spendProfileId)
        .add("properties=" + properties)
        .add("workspaceStage=" + workspaceStage)
        .add("createdByEmail='" + createdByEmail + "'")
        .add("createdDate=" + createdDate)
        .add("state=" + state)
        .add("flightId='" + flightId + "'")
        .add("error=" + error)
        .toString();
  }
}
