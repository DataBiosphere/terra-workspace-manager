package bio.terra.workspace.service.resource.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import javax.annotation.Nullable;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

/**
 * Enum describing how to treat a resource when its containing workspace is cloned. The values
 * stored with the resources may be overridden at clone time.
 *
 * <p>COPY_NOTHING: Don't clone resource.
 *
 * <p>COPY_DEFINITION: Only used for controlled resources. Create new controlled resource and new
 * cloud resource with same metadata, but don't copy any data. For example for GCS bucket, create
 * new GCS bucket with same region/lifecycle rules as source bucket. Files will not be copied over.
 *
 * <p>COPY_RESOURCE: Only used for controlled resources. Create new controlled resource and new
 * cloud resource, with data copied over. For example for GCS bucket, create new GCS bucket with
 * same region/lifecycle rules as source bucket. Copy files from source bucket to new bucket.
 *
 * <p>COPY_REFERENCE: Used for controlled and referenced resources. Create new referenced resource
 * that points to same cloud resource as source resource.
 *
 * <p>LINK_REFERENCE: Used for controlled and referenced resources. Create a new referenced resource
 * that points to the same cloud resource as the source resource, AND link the source workspace
 * policy to the destination workspace policy; changes in the source will propagate to the
 * destination.
 */
public enum CloningInstructions {
  COPY_NOTHING(ApiCloningInstructionsEnum.COPY_NOTHING, "COPY_NOTHING", true, true),
  COPY_DEFINITION(ApiCloningInstructionsEnum.COPY_DEFINITION, "COPY_DEFINITION", true, false),
  COPY_RESOURCE(ApiCloningInstructionsEnum.COPY_RESOURCE, "COPY_RESOURCE", true, false),
  COPY_REFERENCE(ApiCloningInstructionsEnum.COPY_REFERENCE, "COPY_REFERENCE", true, true),
  LINK_REFERENCE(ApiCloningInstructionsEnum.LINK_REFERENCE, "LINK_REFERENCE", true, true);

  private final ApiCloningInstructionsEnum apiInstruction;
  private final String dbInstruction;
  private final boolean validForControlledResource;
  private final boolean validForReferencedResource;

  CloningInstructions(
      ApiCloningInstructionsEnum apiInstruction,
      String dbInstruction,
      boolean validForControlledResource,
      boolean validForReferencedResource) {
    this.apiInstruction = apiInstruction;
    this.dbInstruction = dbInstruction;
    this.validForControlledResource = validForControlledResource;
    this.validForReferencedResource = validForReferencedResource;
  }

  public boolean isValidForControlledResource() {
    return validForControlledResource;
  }

  public boolean isValidForReferencedResource() {
    return validForReferencedResource;
  }

  public ApiCloningInstructionsEnum toApiModel() {
    return apiInstruction;
  }

  /** Convert this to a String to be serialized to the DB. */
  public String toSql() {
    return dbInstruction;
  }

  public static @Nullable CloningInstructions fromApiModel(
      @Nullable ApiCloningInstructionsEnum api) {
    if (null == api) {
      return null;
    }
    for (CloningInstructions instruction : values()) {
      if (instruction.apiInstruction == api) {
        return instruction;
      }
    }
    throw new ValidationException("Invalid cloning instruction");
  }

  /** Deserialize a string from DB. */
  public static CloningInstructions fromSql(String dbString) {
    for (CloningInstructions instruction : values()) {
      if (StringUtils.equals(instruction.dbInstruction, dbString)) {
        return instruction;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching cloning instruction for " + dbString);
  }

  public boolean isReferenceClone() {
    return (this == COPY_REFERENCE || this == LINK_REFERENCE);
  }
}
