package bio.terra.workspace.service.datareference.model;

import com.google.auto.value.AutoValue;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * This is an internal representation of a request to create a data reference.
 *
 * <p>While this object is similar to the {@code DataReference} object, there is a semantic
 * difference: a {@code DataReference} is a fully formed data reference, while a {@code
 * DataReferenceRequest} only contains fields specified by clients. Notably, a DataReferenceRequest
 * does not have a referenceId, which is generated as part of creating a data reference.
 */
@AutoValue
public abstract class DataReferenceRequest {

  /** ID of the workspace this reference belongs to. */
  public abstract UUID workspaceId();

  /**
   * Name of the reference. Names are unique per workspace, per reference type and user-provided.
   */
  public abstract String name();

  /** Description of the reference. */
  @Nullable
  public abstract String description();

  /** Type of this data reference. */
  public abstract DataReferenceType referenceType();

  /** Instructions for how to clone this reference (if at all). */
  public abstract CloningInstructions cloningInstructions();

  /** The actual object being referenced. */
  public abstract ReferenceObject referenceObject();

  /** For controlled resources, FK to the workspace_resource table */
  @Nullable
  public abstract UUID resourceId();

  public static DataReferenceRequest.Builder builder() {
    return new AutoValue_DataReferenceRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract DataReferenceRequest.Builder workspaceId(UUID value);

    public abstract DataReferenceRequest.Builder name(String value);

    public abstract DataReferenceRequest.Builder description(String value);

    public abstract DataReferenceRequest.Builder referenceType(DataReferenceType value);

    public abstract DataReferenceRequest.Builder cloningInstructions(CloningInstructions value);

    public abstract DataReferenceRequest.Builder referenceObject(ReferenceObject value);

    public abstract DataReferenceRequest.Builder resourceId(UUID value);

    public abstract DataReferenceRequest build();
  }
}
