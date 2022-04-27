package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import java.util.List;
import java.util.UUID;

class BucketCloneInputs {

  private final UUID workspaceUuid;
  private final String projectId;
  private final String bucketName;
  private final List<String> roleNames;

  public BucketCloneInputs(
      UUID workspaceUuid, String projectId, String bucketName, List<String> roleNames) {
    this.workspaceUuid = workspaceUuid;
    this.projectId = projectId;
    this.bucketName = bucketName;
    this.roleNames = roleNames;
  }

  public UUID getWorkspaceUuid() {
    return workspaceUuid;
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
        + "workspaceUuid="
        + workspaceUuid
        + ", projectId='"
        + projectId
        + "', bucketName='"
        + bucketName
        + "', roles="
        + roleNames
        + '}';
  }
}
