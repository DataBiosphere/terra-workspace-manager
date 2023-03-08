package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.List;
import javax.annotation.Nullable;

public record WorkspaceDescription(
    Workspace workspace, WsmIamRole highestRole, @Nullable List<String> missingAuthDomains) {}
