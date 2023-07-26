package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GcpCloudContextFields {
  public static final String GCP_CONTEXT_DEFAULT_ZONE = "us-central1-a";

  private final String gcpProjectId;
  private final String samPolicyOwner;
  private final String samPolicyWriter;
  private final String samPolicyReader;
  private final String samPolicyApplication;
  private final String defaultZone;

  @JsonCreator
  public GcpCloudContextFields(
      @JsonProperty("gcpProjectId") String gcpProjectId,
      @JsonProperty("samPolicyOwner") String samPolicyOwner,
      @JsonProperty("samPolicyWriter") String samPolicyWriter,
      @JsonProperty("samPolicyReader") String samPolicyReader,
      @JsonProperty("samPolicyApplication") String samPolicyApplication,
      @JsonProperty("defaultZone") String defaultZone) {
    this.gcpProjectId = gcpProjectId;
    this.samPolicyOwner = samPolicyOwner;
    this.samPolicyWriter = samPolicyWriter;
    this.samPolicyReader = samPolicyReader;
    this.samPolicyApplication = samPolicyApplication;
    this.defaultZone = defaultZone;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  public String getSamPolicyOwner() {
    return samPolicyOwner;
  }

  public String getSamPolicyWriter() {
    return samPolicyWriter;
  }

  public String getSamPolicyReader() {
    return samPolicyReader;
  }

  public String getSamPolicyApplication() {
    return samPolicyApplication;
  }

  public String getDefaultZone() {
    return defaultZone;
  }

  public void toApi(ApiGcpContext gcpContext) {
    gcpContext.projectId(gcpProjectId);
  }

  public String serialize() {
    GcpCloudContextV3 dbContext = new GcpCloudContextV3(this);
    return DbSerDes.toJson(dbContext);
  }

  /**
   * Deserialize a database entry into a GCP cloud context.
   *
   * <p>This can return an empty optional if the provided DB entry is in the CREATING state. These
   * are placeholder entries used for state management, and do not have the context (policy groups,
   * project ID) that callers would expect from a fully-completed GCP cloud context.
   *
   * @param dbContext JSON string from the database
   * @return A deserialized GcpCloudContextFields object.
   */
  public static GcpCloudContextFields deserialize(String dbContext) {
    try {
      // The V3 deserializer will successfully deserialize V2 cloud contexts and
      // set the defaultZone field to null.
      GcpCloudContextV3 v3Context = DbSerDes.fromJson(dbContext, GcpCloudContextV3.class);
      if (v3Context.version != GcpCloudContextV3.GCP_CLOUD_CONTEXT_DB_VERSION
          && v3Context.version != GcpCloudContextV2.GCP_CLOUD_CONTEXT_DB_VERSION) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }

      // Data should not be null
      if (v3Context.samPolicyOwner == null
          || v3Context.samPolicyWriter == null
          || v3Context.samPolicyReader == null
          || v3Context.samPolicyApplication == null) {
        throw new InvalidSerializedVersionException("Serialized version missing data");
      }

      return new GcpCloudContextFields(
          v3Context.gcpProjectId,
          v3Context.samPolicyOwner,
          v3Context.samPolicyWriter,
          v3Context.samPolicyReader,
          v3Context.samPolicyApplication,
          // TODO: PF-2869 Remove this extra logic when backfill has run in all environments
          (v3Context.defaultZone == null ? GCP_CONTEXT_DEFAULT_ZONE : v3Context.defaultZone));
    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version", e);
    }
  }

  // TODO: PF-2869 remove this class when backfill has run in all environments
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

    public GcpCloudContextV2(GcpCloudContextFields gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.samPolicyOwner = gcpCloudContext.samPolicyOwner;
      this.samPolicyWriter = gcpCloudContext.samPolicyWriter;
      this.samPolicyReader = gcpCloudContext.samPolicyReader;
      this.samPolicyApplication = gcpCloudContext.samPolicyApplication;
    }
  }

  public static class GcpCloudContextV3 {
    public static final long GCP_CLOUD_CONTEXT_DB_VERSION = 3;

    /** Version marker to store in the db so that we can update the format later if we need to. */
    public long version;

    public String gcpProjectId;
    public String samPolicyOwner;
    public String samPolicyWriter;
    public String samPolicyReader;
    public String samPolicyApplication;
    public String defaultZone;

    @JsonCreator
    public GcpCloudContextV3(
        @JsonProperty("version") long version,
        @JsonProperty("gcpProjectId") String gcpProjectId,
        @JsonProperty("samPolicyOwner") String samPolicyOwner,
        @JsonProperty("samPolicyWriter") String samPolicyWriter,
        @JsonProperty("samPolicyReader") String samPolicyReader,
        @JsonProperty("samPolicyApplication") String samPolicyApplication,
        @JsonProperty("defaultZone") String defaultZone) {
      this.version = version;
      this.gcpProjectId = gcpProjectId;
      this.samPolicyOwner = samPolicyOwner;
      this.samPolicyWriter = samPolicyWriter;
      this.samPolicyReader = samPolicyReader;
      this.samPolicyApplication = samPolicyApplication;
      this.defaultZone = defaultZone;
    }

    public GcpCloudContextV3(GcpCloudContextFields gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.samPolicyOwner = gcpCloudContext.samPolicyOwner;
      this.samPolicyWriter = gcpCloudContext.samPolicyWriter;
      this.samPolicyReader = gcpCloudContext.samPolicyReader;
      this.samPolicyApplication = gcpCloudContext.samPolicyApplication;
      this.defaultZone = gcpCloudContext.defaultZone;
    }
  }
}
