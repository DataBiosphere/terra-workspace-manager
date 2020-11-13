package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.service.datareference.model.DataReferenceType;
import com.google.auto.value.AutoValue;
import java.util.UUID;

/**
 * Internal representation of an uncontrolled data reference.
 *
 * "Uncontrolled" here means that WM does not own the lifecycle of the underlying data.
 */
@AutoValue
public abstract class DataReference {

  /** ID of the workspace this reference belongs to. */
  public abstract UUID workspaceId();

  /** ID of the reference itself. */
  public abstract UUID referenceId();

  /** Name of the reference. Names are unique per workspace, per reference type. */
  public abstract  String name();

  /** Type of this data reference. */
  public abstract DataReferenceType referenceType();

  /** Instructions for how to clone this reference (if at all). */
  public abstract CloningInstructions cloningInstructions();

  /** The actual object being referenced. TODO: this type should be revisited in the future. */
  public abstract SnapshotReference snapshotReference();

}
