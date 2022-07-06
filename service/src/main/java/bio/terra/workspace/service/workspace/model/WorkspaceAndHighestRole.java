package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.iam.model.WsmIamRole;

public record WorkspaceAndHighestRole(Workspace workspace, WsmIamRole highestRole) {}
