package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Api fields in WsmResource that is not stored as common fields in the WsmResource and needs to be
 * fetched elsewhere.
 */
public record WsmResourceApiFields(String lastUpdatedBy, OffsetDateTime lastUpdatedDate) {

  public static WsmResourceApiFields build(
      WorkspaceActivityLogService service, WsmResource resource) {
    Optional<ActivityLogChangeDetails> logChangeDetails =
        service.getLastUpdatedDetails(
            resource.getWorkspaceId(), resource.getResourceId().toString());
    return new WsmResourceApiFields(
        // There can be a race when the `WorkspaceActivityLogHook` log a resource creation activity
        // and we fetch the resource from the database. So if that occurs, we will use the
        // createdBy and createdDate instead.
        logChangeDetails
            .map(ActivityLogChangeDetails::actorEmail)
            .orElse(resource.getCreatedByEmail()),
        logChangeDetails
            .map(ActivityLogChangeDetails::changeDate)
            .orElse(resource.getCreatedDate()));
  }
}
