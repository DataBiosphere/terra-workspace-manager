package bio.terra.workspace.service.resource.model;

import java.time.OffsetDateTime;

/**
 * Api fields in WsmResource that is not stored as common fields in the WsmResource and needs to be
 * fetched elsewhere.
 */
public record WsmResourceApiFields(String lastUpdatedBy, OffsetDateTime lastUpdatedDate) {}
