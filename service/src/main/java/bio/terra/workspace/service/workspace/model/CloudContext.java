package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import javax.annotation.Nullable;

/**
 * Interface for all cloud contexts. This allows simpler context passing and processing in the
 * common cloud context flight, without changing the class serdes.
 */
public interface CloudContext {
  /** get the cloud platform of the context */
  CloudPlatform getCloudPlatform();

  /** get the spend profile of the context */
  @Nullable
  CloudContextCommonFields getCommonFields();

  /** Get the subtype from the cloud platform */
  @SuppressWarnings("unchecked")
  default <T> T castByEnum(CloudPlatform cloudPlatform) {
    if (cloudPlatform != getCloudPlatform()) {
      throw new InternalLogicException(
          String.format("Invalid cast from %s to %s", getCloudPlatform(), cloudPlatform));
    }
    return (T) this;
  }

  /**
   * Create the serialized form of cloud-specific data to be stored in the database. If no data
   * needs to be stored, return null.
   */
  @Nullable
  String serialize();
}
