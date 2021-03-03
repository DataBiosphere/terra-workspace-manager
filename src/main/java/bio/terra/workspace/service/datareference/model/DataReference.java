package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.ReferenceTypeEnum;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.google.auto.value.AutoValue;

import java.util.UUID;

/**
 * Internal representation of an uncontrolled data reference.
 *
 * <p>"Uncontrolled" here means that WM does not own the lifecycle of the underlying data.
 */
@AutoValue
public abstract class DataReference {

  /** ID of the workspace this reference belongs to. */
  public abstract UUID workspaceId();

  /** ID of the reference itself. */
  public abstract UUID referenceId();

  /** Name of the reference. Names are unique per workspace, per reference type. */
  public abstract String name();

  /** Description of the data reference. */
  public abstract String description();

  /** Type of this data reference. */
  public abstract WsmResourceType referenceType();

  /** Instructions for how to clone this reference (if at all). */
  public abstract CloningInstructions cloningInstructions();

  /** The actual object being referenced. */
  public abstract ReferenceObject referenceObject();

  /** Convenience method for translating to an API model DataReferenceDescription object. */
  // TODO(PF-404): This is only used by the deprecated reference APIs. Remove this when all clients
  //  have migrated. That is why we can hard code the reference type.
  public DataReferenceDescription toApiModel() {
    return new DataReferenceDescription()
        .referenceId(referenceId())
        .name(name())
        .description(description())
        .workspaceId(workspaceId())
        .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
        .reference(((SnapshotReference) referenceObject()).toApiModel())
        .cloningInstructions(cloningInstructions().toApiModel());
  }

  public static Builder builder() {
    return new AutoValue_DataReference.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract DataReference.Builder workspaceId(UUID value);

    public abstract DataReference.Builder referenceId(UUID value);

    public abstract DataReference.Builder name(String value);

    public abstract DataReference.Builder description(String value);

    public abstract DataReference.Builder referenceType(WsmResourceType value);

    public abstract DataReference.Builder cloningInstructions(CloningInstructions value);

    public abstract DataReference.Builder referenceObject(ReferenceObject value);

    public abstract DataReference build();
  }
}
