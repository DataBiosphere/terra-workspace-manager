package bio.terra.workspace.db.model;

import javax.annotation.Nullable;

/**
 * This record is used in the bulk retrieval query that gets a workspace and any associated cloud
 * contexts in one query.
 *
 * @param dbWorkspace
 * @param dbCloudContext
 */
public record DbWorkspaceContextPair(
    DbWorkspace dbWorkspace, @Nullable DbCloudContext dbCloudContext) {}
