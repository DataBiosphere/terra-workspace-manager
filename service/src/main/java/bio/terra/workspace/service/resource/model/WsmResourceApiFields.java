package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Api fields in WsmResource that is not stored as common fields in the WsmResource and needs to be
 * fetched elsewhere.
 */
public record WsmResourceApiFields(String lastUpdatedBy, OffsetDateTime lastUpdatedDate) {

  public static WsmResourceApiFields build(
      WorkspaceActivityLogService service, UUID workspaceId, UUID resourceId) {
    Optional<ActivityLogChangeDetails> logChangeDetails =
        service.getLastUpdatedDetails(workspaceId, resourceId.toString());
    return new WsmResourceApiFields(
        logChangeDetails.map(ActivityLogChangeDetails::actorEmail).orElse("unknown"),
        logChangeDetails.map(ActivityLogChangeDetails::changeDate).orElse(OffsetDateTime.MIN));
  }
}
