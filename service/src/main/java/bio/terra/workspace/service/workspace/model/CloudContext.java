package bio.terra.workspace.service.workspace.model;

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
  <T> T castByEnum(CloudPlatform cloudPlatform);

  /**
   * Create the serialized form of cloud-specific data to be stored in the database. If no data
   * needs to be stored, return null.
   */
  @Nullable
  String serialize();
}
