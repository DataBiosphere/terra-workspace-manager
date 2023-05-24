package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.SerializationException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GcpCloudContextFields {
  private final String gcpProjectId;
  private final String samPolicyOwner;
  private final String samPolicyWriter;
  private final String samPolicyReader;
  private final String samPolicyApplication;

  @JsonCreator
  public GcpCloudContextFields(
      @JsonProperty("gcpProjectId") String gcpProjectId,
      @JsonProperty("samPolicyOwner") String samPolicyOwner,
      @JsonProperty("samPolicyWriter") String samPolicyWriter,
      @JsonProperty("samPolicyReader") String samPolicyReader,
      @JsonProperty("samPolicyApplication") String samPolicyApplication) {
    this.gcpProjectId = gcpProjectId;
    this.samPolicyOwner = samPolicyOwner;
    this.samPolicyWriter = samPolicyWriter;
    this.samPolicyReader = samPolicyReader;
    this.samPolicyApplication = samPolicyApplication;
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

  public ApiGcpContext toApi() {
    return new ApiGcpContext().projectId(gcpProjectId);
  }

  public String serialize() {
    GcpCloudContextV2 dbContext = new GcpCloudContextV2(this);
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
      GcpCloudContextV2 v2Context = DbSerDes.fromJson(dbContext, GcpCloudContextV2.class);
      if (v2Context.version != GcpCloudContextV2.GCP_CLOUD_CONTEXT_DB_VERSION) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }

      // Data should not be null
      if (v2Context.samPolicyOwner == null
          || v2Context.samPolicyWriter == null
          || v2Context.samPolicyReader == null
          || v2Context.samPolicyApplication == null) {
        throw new InvalidSerializedVersionException("Serialized version missing data");
      }

      return new GcpCloudContextFields(
          v2Context.gcpProjectId,
          v2Context.samPolicyOwner,
          v2Context.samPolicyWriter,
          v2Context.samPolicyReader,
          v2Context.samPolicyApplication);
    } catch (SerializationException e) {
      throw new InvalidSerializedVersionException("Invalid serialized version", e);
    }
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

    public GcpCloudContextV2(GcpCloudContextFields gcpCloudContext) {
      this.version = GCP_CLOUD_CONTEXT_DB_VERSION;
      this.gcpProjectId = gcpCloudContext.gcpProjectId;
      this.samPolicyOwner = gcpCloudContext.samPolicyOwner;
      this.samPolicyWriter = gcpCloudContext.samPolicyWriter;
      this.samPolicyReader = gcpCloudContext.samPolicyReader;
      this.samPolicyApplication = gcpCloudContext.samPolicyApplication;
    }
  }
}
