package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.workspace.exceptions.AzureNotImplementedException;
import bio.terra.workspace.service.workspace.exceptions.InternalLogicException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

/**
 * A cloud context associated with a workspace and some resources within the workspace. This defines
 * the interface for the cloud context.
 *
 * <p>We expect all implementations of the interface to have a static deserialize method like this:
 * WorkspaceCloudContext deserialize(ObjectMapper objectMapper, String json);
 */
public interface WorkspaceCloudContext {
  /** get the cloud type of this cloud context */
  CloudType getCloudType();

  /** get the cloud context id of this cloud context */
  UUID getCloudContextId();

  /** serialize the cloud context for database storage */
  String serialize(ObjectMapper objectMapper);

  /**
   * helper method to dispatch by cloud type to deserialize into an implementation of
   * WorkspaceCloudContext
   */
  static WorkspaceCloudContext deserialize(
      ObjectMapper objectMapper, CloudType cloudType, UUID cloudContextId, String json) {
    switch (cloudType) {
      case GCP:
        return GcpCloudContext.deserialize(objectMapper, json, cloudContextId);
      case AZURE:
        throw new AzureNotImplementedException("Azure cloud context is not yet implemented");
      default:
        throw new InternalLogicException("Invalid cloud type: " + cloudType);
    }
  }
}
