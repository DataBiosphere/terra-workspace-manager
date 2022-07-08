package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.time.Instant;
import java.util.Optional;

/**
 * Workspace and a set of attributes that are part of the {@link
 * bio.terra.workspace.generated.model.ApiWorkspaceDescription} but not stored in the workspace data
 * table and needed to be fetched elsewhere.
 */
public record WorkspaceAndAdditionalAttributes(
    Workspace workspace, WsmIamRole highestRole, Optional<Instant> lastUpdatedDate) {}
