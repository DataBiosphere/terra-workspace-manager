package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import javax.annotation.Nullable;

public class GcpCloudContext {
  private String gcpProjectId;
  private String gcpDefaultZone;
  // V2 additions:
  // - Sam policy groups for the workspace roles; nullable
  @Nullable private String samPolicyOwner;
  @Nullable private String samPolicyWriter;
  @Nullable private String samPolicyReader;
  @Nullable private String samPolicyApplication;

  // Constructor for Jackson
  public GcpCloudContext() {}

  // Constructor for V1
  public GcpCloudContext(String gcpProjectId) {
    this.gcpProjectId = gcpProjectId;
    this.gcpDefaultZone = null;
    this.samPolicyOwner = null;
    this.samPolicyWriter = null;
    this.samPolicyReader = null;
    this.samPolicyApplication = null;
  }

  public GcpCloudContext(String gcpProjectId, String gcpDefaultZone) {
    this.gcpProjectId = gcpProjectId;
    this.gcpDefaultZone = gcpDefaultZone;
    this.samPolicyOwner = null;
    this.samPolicyWriter = null;
    this.samPolicyReader = null;
    this.samPolicyApplication = null;
  }

  // Constructor for V2
  public GcpCloudContext(
      String gcpProjectId,
      //      String gcpDefaultZone,
      @Nullable String samPolicyOwner,
      @Nullable String samPolicyWriter,
      @Nullable String samPolicyReader,
      @Nullable String samPolicyApplication) {
    this.gcpProjectId = gcpProjectId;
    //    this.gcpDefaultZone = gcpDefaultZone;
    this.samPolicyOwner = samPolicyOwner;
    this.samPolicyWriter = samPolicyWriter;
    this.samPolicyReader = samPolicyReader;
    this.samPolicyApplication = samPolicyApplication;
  }

  public GcpCloudContext(
      String gcpProjectId,
      String gcpDefaultZone,
      @Nullable String samPolicyOwner,
      @Nullable String samPolicyWriter,
      @Nullable String samPolicyReader,
      @Nullable String samPolicyApplication) {
    this.gcpProjectId = gcpProjectId;
    this.gcpDefaultZone = gcpDefaultZone;
    this.samPolicyOwner = samPolicyOwner;
    this.samPolicyWriter = samPolicyWriter;
    this.samPolicyReader = samPolicyReader;
    this.samPolicyApplication = samPolicyApplication;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public String getGcpDefaultZone() {
    return gcpDefaultZone;
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

  public void setSamPolicyOwner(String samPolicyOwner) {
    this.samPolicyOwner = samPolicyOwner;
  }

  public void setSamPolicyWriter(String samPolicyWriter) {
    this.samPolicyWriter = samPolicyWriter;
  }

  public void setSamPolicyReader(String samPolicyReader) {
    this.samPolicyReader = samPolicyReader;
  }

  public void setSamPolicyApplication(String samPolicyApplication) {
    this.samPolicyApplication = samPolicyApplication;
  }

  public void setGcpDefaultZone(String gcpDefaultZone) {
    this.gcpDefaultZone = gcpDefaultZone;
  }

  public ApiGcpContext toApi() {
    return new ApiGcpContext().projectId(gcpProjectId).gcpDefaultZone(gcpDefaultZone);
  }

  public String serialize() {
    // We use the owner policy to know when we have V1 data
    if (samPolicyOwner == null) {
      GcpCloudContextV1 v1Context = new GcpCloudContextV1(this);
      return DbSerDes.toJson(v1Context);
    }
    GcpCloudContextV2 dbContext = new GcpCloudContextV2(this);
    return DbSerDes.toJson(dbContext);
  }

  public static @Nullable GcpCloudContext deserialize(@Nullable String json) {
    if (json == null) {
      return null;
    }

    try {
      GcpCloudContextV2 v2Context = DbSerDes.fromJson(json, GcpCloudContextV2.class);
      if (v2Context.version == GcpCloudContextV2.GCP_CLOUD_CONTEXT_DB_VERSION) {
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
            v2Context.gcpDefaultZone,
            v2Context.samPolicyOwner,
            v2Context.samPolicyWriter,
            v2Context.samPolicyReader,
            v2Context.samPolicyApplication);
      }
    } catch (SerializationException e) {
      // Deserialization of V2 failed. Try the V1 format
    }
    // TODO(PF-1666): Remove this branch once all workspaces are migrated to GcpCloudContextV2
    try {
      GcpCloudContextV1 v1Context = DbSerDes.fromJson(json, GcpCloudContextV1.class);
      if (v1Context.version == GcpCloudContextV1.GCP_CLOUD_CONTEXT_DB_VERSION) {
        return new GcpCloudContext(v1Context.gcpProjectId, v1Context.gcpDefaultZone);
      }
    } catch (SerializationException e) {
      // Deserialization of V1 failed.
    }

    throw new InvalidSerializedVersionException("Invalid serialized version");
  }

  public static class GcpCloudContextV1 {
    public static final long GCP_CLOUD_CONTEXT_DB_VERSION = 1;
    public long version;
    public String gcpProjectId;
    public String gcpDefaultZone;

    @JsonCreator
    public GcpCloudContextV1(
        @JsonProperty("version") long version,
        @JsonProperty("gcpProjectId") String gcpProjectId,
        @JsonProperty("gcpDefaultZone") String gcpDefaultZone) {
      this.version = version;
      this.gcpProjectId = gcpProjectId;
      this.gcpDefaultZone = gcpDefaultZone;
    }

    public GcpCloudContextV1(GcpCloudContext gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.gcpDefaultZone = gcpCloudContext.gcpDefaultZone;
    }
  }

  public static class GcpCloudContextV2 {
    public static final long GCP_CLOUD_CONTEXT_DB_VERSION = 2;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String gcpProjectId;
    public String gcpDefaultZone;
    public String samPolicyOwner;
    public String samPolicyWriter;
    public String samPolicyReader;
    public String samPolicyApplication;

    @JsonCreator
    public GcpCloudContextV2(
        @JsonProperty("version") long version,
        @JsonProperty("gcpProjectId") String gcpProjectId,
        @JsonProperty("gcpDefaultZone") String gcpDefaultZone,
        @JsonProperty("samPolicyOwner") String samPolicyOwner,
        @JsonProperty("samPolicyWriter") String samPolicyWriter,
        @JsonProperty("samPolicyReader") String samPolicyReader,
        @JsonProperty("samPolicyApplication") String samPolicyApplication) {
      this.version = version;
      this.gcpProjectId = gcpProjectId;
      this.gcpDefaultZone = gcpDefaultZone;
      this.samPolicyOwner = samPolicyOwner;
      this.samPolicyWriter = samPolicyWriter;
      this.samPolicyReader = samPolicyReader;
      this.samPolicyApplication = samPolicyApplication;
    }

    public GcpCloudContextV2(GcpCloudContext gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.gcpDefaultZone = gcpCloudContext.gcpDefaultZone;
      this.samPolicyOwner = gcpCloudContext.samPolicyOwner;
      this.samPolicyWriter = gcpCloudContext.samPolicyWriter;
      this.samPolicyReader = gcpCloudContext.samPolicyReader;
      this.samPolicyApplication = gcpCloudContext.samPolicyApplication;
    }
  }
}
