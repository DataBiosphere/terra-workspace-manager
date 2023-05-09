package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import javax.annotation.Nullable;

public class GcpCloudContext implements CloudContext {
  private final String gcpProjectId;
  private final String samPolicyOwner;
  private final String samPolicyWriter;
  private final String samPolicyReader;
  private final String samPolicyApplication;
  private final @Nullable CloudContextCommonFields commonFields;

  @JsonCreator
  public GcpCloudContext(
      @JsonProperty("gcpProjectId") String gcpProjectId,
      @JsonProperty("samPolicyOwner") String samPolicyOwner,
      @JsonProperty("samPolicyWriter") String samPolicyWriter,
      @JsonProperty("samPolicyReader") String samPolicyReader,
      @JsonProperty("samPolicyApplication") String samPolicyApplication,
      @JsonProperty("commonFields") @Nullable CloudContextCommonFields commonFields) {
    this.gcpProjectId = gcpProjectId;
    this.samPolicyOwner = samPolicyOwner;
    this.samPolicyWriter = samPolicyWriter;
    this.samPolicyReader = samPolicyReader;
    this.samPolicyApplication = samPolicyApplication;
    this.commonFields = commonFields;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public Optional<String> getSamPolicyOwner() {
    return Optional.ofNullable(samPolicyOwner);
  }

  public Optional<String> getSamPolicyWriter() {
    return Optional.ofNullable(samPolicyWriter);
  }

  public Optional<String> getSamPolicyReader() {
    return Optional.ofNullable(samPolicyReader);
  }

  public Optional<String> getSamPolicyApplication() {
    return Optional.ofNullable(samPolicyApplication);
  }

  @Override
  public CloudPlatform getCloudPlatform() {
    return CloudPlatform.GCP;
  }

  @Override
  public @Nullable CloudContextCommonFields getCommonFields() {
    return commonFields;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T castByEnum(CloudPlatform cloudPlatform) {
    if (cloudPlatform != getCloudPlatform()) {
      throw new InternalLogicException(
          String.format("Invalid cast from %s to %s", getCloudPlatform(), cloudPlatform));
    }
    return (T) this;
  }

  @Override
  public String serialize() {
    GcpCloudContextV2 dbContext = new GcpCloudContextV2(this);
    return DbSerDes.toJson(dbContext);
  }

  public static GcpCloudContext deserialize(DbCloudContext dbCloudContext) {
    // Sanity check the input
    if (dbCloudContext == null
        || dbCloudContext.getSpendProfile() == null
        || dbCloudContext.getCloudPlatform() != CloudPlatform.GCP) {
      throw new InternalLogicException("Invalid GCP cloud context state");
    }

    GcpCloudContextV2 v2Context =
        DbSerDes.fromJson(dbCloudContext.getContextJson(), GcpCloudContextV2.class);
    if (v2Context.version != GcpCloudContextV2.GCP_CLOUD_CONTEXT_DB_VERSION) {
      throw new InvalidSerializedVersionException("Invalid serialized version");
    }

    // If the set of fields is incomplete, this will deserialize and fill in nulls.
    // Those are valid in the GcpCloudContext, because it needs to model V1
    // contexts. However, we should not see nulls here, so we explicitly validate.
    if (v2Context.samPolicyOwner == null
        || v2Context.samPolicyWriter == null
        || v2Context.samPolicyReader == null
        || v2Context.samPolicyApplication == null) {
      throw new InvalidSerializedVersionException("Serialized version missing data");
    }

    return new GcpCloudContext(
        v2Context.gcpProjectId,
        v2Context.samPolicyOwner,
        v2Context.samPolicyWriter,
        v2Context.samPolicyReader,
        v2Context.samPolicyApplication,
        new CloudContextCommonFields(
            dbCloudContext.getSpendProfile(),
            dbCloudContext.getState(),
            dbCloudContext.getFlightId(),
            dbCloudContext.getError()));
  }

  public ApiGcpContext toApi() {
    return new ApiGcpContext().projectId(gcpProjectId);
  }

  public static class GcpCloudContextV2 {
    public static final long GCP_CLOUD_CONTEXT_DB_VERSION = 2;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String gcpProjectId;
    public String samPolicyOwner;
    public String samPolicyWriter;
    public String samPolicyReader;
    public String samPolicyApplication;

    @JsonCreator
    public GcpCloudContextV2(
        @JsonProperty("version") long version,
        @JsonProperty("gcpProjectId") String gcpProjectId,
        @JsonProperty("samPolicyOwner") String samPolicyOwner,
        @JsonProperty("samPolicyWriter") String samPolicyWriter,
        @JsonProperty("samPolicyReader") String samPolicyReader,
        @JsonProperty("samPolicyApplication") String samPolicyApplication) {
      this.version = version;
      this.gcpProjectId = gcpProjectId;
      this.samPolicyOwner = samPolicyOwner;
      this.samPolicyWriter = samPolicyWriter;
      this.samPolicyReader = samPolicyReader;
      this.samPolicyApplication = samPolicyApplication;
    }

    public GcpCloudContextV2(GcpCloudContext gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.samPolicyOwner = gcpCloudContext.samPolicyOwner;
      this.samPolicyWriter = gcpCloudContext.samPolicyWriter;
      this.samPolicyReader = gcpCloudContext.samPolicyReader;
      this.samPolicyApplication = gcpCloudContext.samPolicyApplication;
    }
  }
}
