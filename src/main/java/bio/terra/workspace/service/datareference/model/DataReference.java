package bio.terra.workspace.service.datareference.model;

import bio.terra.workspace.generated.model.DataReferenceDescription;
import bio.terra.workspace.generated.model.DataReferenceInfo;
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

  /** Type of this data reference. */
  public abstract DataReferenceType referenceType();

  /** Instructions for how to clone this reference (if at all). */
  public abstract CloningInstructions cloningInstructions();

  /** The actual object being referenced. */
  public abstract ReferenceObject referenceObject();

  /** Convenience method for translating to an API model DataReferenceDescription object. */
  public DataReferenceDescription toApiModel() {
    DataReferenceDescription reference =
        new DataReferenceDescription()
            .referenceId(referenceId())
            .name(name())
            .workspaceId(workspaceId())
            .referenceType(referenceType().toApiModel())
            .referenceInfo(new DataReferenceInfo())
            .cloningInstructions(cloningInstructions().toApiModel());
    switch (referenceType()) {
      case DATA_REPO_SNAPSHOT:
        // TODO: we populate both the deprecated reference field and the new referenceInfo fields
        // until all clients have migrated to use the new field.
        reference.reference(((SnapshotReference) referenceObject()).toApiModel());
        reference
            .getReferenceInfo()
            .dataRepoSnapshot(((SnapshotReference) referenceObject()).toApiModel());
        break;
      case GOOGLE_BUCKET:
        reference
            .getReferenceInfo()
            .googleBucket(((GoogleBucketReference) referenceObject()).toApiModel());
        break;
      case BIG_QUERY_DATASET:
        reference
            .getReferenceInfo()
            .bigQueryDataset(((BigQueryDatasetReference) referenceObject()).toApiModel());
        break;
    }
    return reference;
  }

  public static Builder builder() {
    return new AutoValue_DataReference.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract DataReference.Builder workspaceId(UUID value);

    public abstract DataReference.Builder referenceId(UUID value);

    public abstract DataReference.Builder name(String value);

    public abstract DataReference.Builder referenceType(DataReferenceType value);

    public abstract DataReference.Builder cloningInstructions(CloningInstructions value);

    public abstract DataReference.Builder referenceObject(ReferenceObject value);

    public abstract DataReference build();
  }
}
