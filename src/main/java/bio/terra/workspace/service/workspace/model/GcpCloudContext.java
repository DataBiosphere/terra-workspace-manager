package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.SerializationException;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

public class GcpCloudContext implements WorkspaceCloudContext {
  private static final long GCP_CLOUD_CONTEXT_DB_VERSION = 1;

  private final String gcpProjectId;
  private final UUID cloudContextId;

  public GcpCloudContext(String gcpProjectId, UUID cloudContextId) {
    this.gcpProjectId = gcpProjectId;
    this.cloudContextId = cloudContextId;
  }

  public String getGcpProjectId() {
    return gcpProjectId;
  }

  @Override
  public CloudType getCloudType() {
    return CloudType.GCP;
  }

  @Override
  public UUID getCloudContextId() {
    return cloudContextId;
  }

  @Override
  public String serialize(ObjectMapper objectMapper) {
    GcpCloudContextV1 dbContext = GcpCloudContextV1.from(gcpProjectId);
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Failed to serialize GcpCloudContextV1", e);
    }
  }

  public static WorkspaceCloudContext deserialize(
      ObjectMapper objectMapper, String json, UUID cloudContextId) {
    try {
      GcpCloudContextV1 result = objectMapper.readValue(json, GcpCloudContextV1.class);
      if (result.version != GCP_CLOUD_CONTEXT_DB_VERSION) {
        throw new InvalidSerializedVersionException("Invalid serialized version");
      }
      return new GcpCloudContext(result.googleProjectId, cloudContextId);
    } catch (JsonProcessingException e) {
      throw new SerializationException("Failed to serialize GcpCloudContextV1", e);
    }
  }

  static class GcpCloudContextV1 {
    /** Version marker to store in the db so that we can update the format later if we need to. */
    @JsonProperty final long version = GCP_CLOUD_CONTEXT_DB_VERSION;

    @JsonProperty String googleProjectId;

    public static GcpCloudContextV1 from(String googleProjectId) {
      GcpCloudContextV1 result = new GcpCloudContextV1();
      result.googleProjectId = googleProjectId;
      return result;
    }
  }
}
