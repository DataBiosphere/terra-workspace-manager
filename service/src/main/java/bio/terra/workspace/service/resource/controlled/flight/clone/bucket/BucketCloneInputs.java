package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

class BucketCloneInputs {

  private final UUID workspaceUuid;
  private final String projectId;
  private final String bucketName;
  private final List<String> roleNames;

  @JsonCreator
  public BucketCloneInputs(
      @JsonProperty("workspaceId") UUID workspaceUuid,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("bucketName") String bucketName,
      @JsonProperty("roleNames") List<String> roleNames) {
    this.workspaceUuid = workspaceUuid;
    this.projectId = projectId;
    this.bucketName = bucketName;
    this.roleNames = roleNames;
  }

  public UUID getWorkspaceId() {
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
