package bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot;

import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.exception.InvalidMetadataException;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotResource;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.flight.UpdateResourceFlight;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReferencedDataRepoSnapshotResource extends ReferencedResource {
  private final String instanceName;
  private final String snapshotId;

  /**
   * Constructor for serialized form for Stairway use
   *
   * @param resourceFields common resource fields
   * @param instanceName name of the data repository instance (e.g., "terra")
   * @param snapshotId name of the snapshot
   */
  @JsonCreator
  public ReferencedDataRepoSnapshotResource(
      @JsonProperty("wsmResourceFields") WsmResourceFields resourceFields,
      @JsonProperty("instanceName") String instanceName,
      @JsonProperty("snapshotId") String snapshotId) {
    super(resourceFields);
    this.instanceName = instanceName;
    this.snapshotId = snapshotId;
    validate();
  }

  private ReferencedDataRepoSnapshotResource(Builder builder) {
    super(builder.wsmResourceFields);
    this.instanceName = builder.instanceName;
    this.snapshotId = builder.snapshotId;
    validate();
  }

  /**
   * Constructor from database metadata
   *
   * @param dbResource database form of resources
   */
  public ReferencedDataRepoSnapshotResource(DbResource dbResource) {
    super(dbResource);
    if (dbResource.getResourceType() != WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT) {
      throw new InvalidMetadataException("Expected referenced DATA_REPO_SNAPSHOT");
    }
    ReferencedDataRepoSnapshotAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ReferencedDataRepoSnapshotAttributes.class);
    this.instanceName = attributes.getInstanceName();
    this.snapshotId = attributes.getSnapshotId();
    validate();
  }

  public static ReferencedDataRepoSnapshotResource.Builder builder() {
    return new ReferencedDataRepoSnapshotResource.Builder();
  }

  // -- getters used in serialization --
  @Override
  public WsmResourceFields getWsmResourceFields() {
    return super.getWsmResourceFields();
  }

  public String getInstanceName() {
    return instanceName;
  }

  public String getSnapshotId() {
    return snapshotId;
  }

  // -- getters not included in serialization --
  @Override
  @JsonIgnore
  public WsmResourceType getResourceType() {
    return WsmResourceType.REFERENCED_ANY_DATA_REPO_SNAPSHOT;
  }

  @Override
  @JsonIgnore
  public WsmResourceFamily getResourceFamily() {
    return WsmResourceFamily.DATA_REPO_SNAPSHOT;
  }

  public ApiDataRepoSnapshotAttributes toApiAttributes() {
    return new ApiDataRepoSnapshotAttributes()
        .instanceName(getInstanceName())
        .snapshot(getSnapshotId());
  }

  public ApiDataRepoSnapshotResource toApiResource() {
    return new ApiDataRepoSnapshotResource()
        .metadata(super.toApiMetadata())
        .attributes(toApiAttributes());
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(WsmResourceType expectedType) {
    if (getResourceType() != expectedType) {
      throw new BadRequestException(String.format("Resource is not a %s", expectedType));
    }
    return (T) this;
  }

  @Override
  public String attributesToJson() {
    return DbSerDes.toJson(
        new ReferencedDataRepoSnapshotAttributes(getInstanceName(), getSnapshotId()));
  }

  @Override
  public ApiResourceAttributesUnion toApiAttributesUnion() {
    return new ApiResourceAttributesUnion().gcpDataRepoSnapshot(toApiAttributes());
  }

  @Override
  public void addUpdateSteps(UpdateResourceFlight flight, FlightBeanBag flightBeanBag) {
    flight.addStep(new UpdateDataRepoSnapshotReferenceStep());
  }

  @Override
  public void validate() {
    super.validate();
    if (Strings.isNullOrEmpty(getInstanceName()) || Strings.isNullOrEmpty(getSnapshotId())) {
      throw new MissingRequiredFieldException(
          "Missing required field for ReferenceDataRepoSnapshotAttributes.");
    }
  }

  @Override
  public boolean checkAccess(FlightBeanBag context, AuthenticatedUserRequest userRequest) {
    DataRepoService dataRepoService = context.getDataRepoService();
    return dataRepoService.snapshotReadable(instanceName, snapshotId, userRequest);
  }

  @Override
  public WsmResource buildReferencedClone(
      UUID destinationWorkspaceUuid,
      UUID destinationResourceId,
      @Nullable UUID destinationFolderId,
      @Nullable String name,
      @Nullable String description,
      String createdByEmail) {
    ReferencedDataRepoSnapshotResource.Builder resultBuilder =
        toBuilder()
            .wsmResourceFields(
                buildReferencedCloneResourceCommonFields(
                    destinationWorkspaceUuid,
                    destinationResourceId,
                    destinationFolderId,
                    name,
                    description,
                    createdByEmail));
    return resultBuilder.build();
  }

  public Builder toBuilder() {
    return builder()
        .instanceName(getInstanceName())
        .snapshotId(getSnapshotId())
        .wsmResourceFields(getWsmResourceFields());
  }

  public static class Builder {
    private String instanceName;
    private String snapshotId;
    private WsmResourceFields wsmResourceFields;

    public Builder instanceName(String instanceName) {
      this.instanceName = instanceName;
      return this;
    }

    public Builder snapshotId(String snapshotId) {
      this.snapshotId = snapshotId;
      return this;
    }

    public Builder wsmResourceFields(WsmResourceFields resourceFields) {
      this.wsmResourceFields = resourceFields;
      return this;
    }

    public ReferencedDataRepoSnapshotResource build() {
      return new ReferencedDataRepoSnapshotResource(this);
    }
  }
}
