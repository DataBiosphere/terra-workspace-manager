package bio.terra.workspace.service.iam.model;

import java.util.List;
import java.util.UUID;

public record AccessibleWorkspace(
    UUID workspaceUuid, WsmIamRole highestRole, List<String> missingAuthDomainGroups) {}
