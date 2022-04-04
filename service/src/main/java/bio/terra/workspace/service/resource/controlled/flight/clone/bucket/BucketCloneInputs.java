package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import java.util.List;
import java.util.UUID;

class BucketCloneInputs {

  private final UUID workspaceId;
  private final String projectId;
  private final String bucketName;
  private final List<String> roleNames;

  public BucketCloneInputs(
      UUID workspaceId, String projectId, String bucketName, List<String> roleNames) {
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.bucketName = bucketName;
    this.roleNames = roleNames;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getBucketName() {
    return bucketName;
  }

  public List<String> getRoleNames() {
    return roleNames;
  }

  @Override
  public String toString() {
    return "BucketInputs{"
        + "workspaceId="
        + workspaceId
        + ", projectId='"
        + projectId
        + "', bucketName='"
        + bucketName
        + "', roles="
        + roleNames
        + '}';
  }
}
