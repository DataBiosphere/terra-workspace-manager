package bio.terra.workspace.service.iam.model;

import java.util.List;
import java.util.UUID;

/**
 * This record is used to return the data for one workspace that the caller has some access to,
 * according to Sam.
 *
 * @param workspaceUuid workspaceUuid of the workspace
 * @param highestRole the highest IAM role the user has on the workspace
 * @param missingAuthDomainGroups any auth domain groups the user is missing in order to access the
 *     workspace
 */
public record AccessibleWorkspace(
    UUID workspaceUuid, WsmIamRole highestRole, List<String> missingAuthDomainGroups) {}
