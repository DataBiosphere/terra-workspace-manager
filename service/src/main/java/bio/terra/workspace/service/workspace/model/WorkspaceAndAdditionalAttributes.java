package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.time.Instant;
import java.util.Optional;

public record WorkspaceAndAdditionalAttributes(
    Workspace workspace, WsmIamRole highestRole, Optional<Instant> lastUpdatedDate) {}
