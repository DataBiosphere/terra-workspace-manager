package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiGcpContext;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.workspace.exceptions.CloudContextNotReadyException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class GcpCloudContext implements CloudContext {
  private final @Nullable GcpCloudContextFields contextFields;
  private final CloudContextCommonFields commonFields;

  @JsonCreator
  public GcpCloudContext(
      @JsonProperty("contextFields") @Nullable GcpCloudContextFields contextFields,
      @JsonProperty("commonFields") CloudContextCommonFields commonFields) {
    this.contextFields = contextFields;
    this.commonFields = commonFields;
  }

  @Nullable
  public GcpCloudContextFields getContextFields() {
    return contextFields;
  }

  @JsonIgnore
  public String getGcpProjectId() {
    checkReady();
    return contextFields.getGcpProjectId();
  }

  @JsonIgnore
  public String getSamPolicyOwner() {
    checkReady();
    return contextFields.getSamPolicyOwner();
  }

  @JsonIgnore
  public String getSamPolicyWriter() {
    checkReady();
    return contextFields.getSamPolicyWriter();
  }

  @JsonIgnore
  public String getSamPolicyReader() {
    checkReady();
    return contextFields.getSamPolicyReader();
  }

  @JsonIgnore
  public String getSamPolicyApplication() {
    checkReady();
    ;
    return contextFields.getSamPolicyApplication();
  }

  @Override
  @JsonIgnore
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

  // TODO: PF-2770 include the common fields in the API return
  public ApiGcpContext toApi() {
    return (contextFields == null ? null : contextFields.toApi());
  }

  /**
   * Test if the cloud context is ready to be used by operations. It must be in the ready state and
   * the context fields must be populated.
   *
   * @return true if the context is in the READY state; false otherwise
   */
  public boolean isReady() {
    return (commonFields.state().equals(WsmResourceState.READY) && contextFields != null);
  }

  /** Throw exception is the cloud context is not ready. */
  public void checkReady() {
    if (!isReady()) {
      throw new CloudContextNotReadyException(
          "Cloud context is not ready. Wait for the context to be ready and try again.");
    }
  }

  @Override
  public String serialize() {
    if (contextFields == null) {
      throw new InternalLogicException("Cannot serialize without context fields filled in");
    }
    return contextFields.serialize();
  }

  /**
   * Deserialize a database entry into a GCP cloud context.
   *
   * @param dbCloudContext Entry from cloud_context DB describing a GCP cloud context
   * @return A deserialized GcpCloudContext object.
   */
  public static GcpCloudContext deserialize(DbCloudContext dbCloudContext) {
    GcpCloudContextFields contextFields =
        (dbCloudContext.getContextJson() == null
            ? null
            : GcpCloudContextFields.deserialize(dbCloudContext.getContextJson()));

    return new GcpCloudContext(
        contextFields,
        new CloudContextCommonFields(
            dbCloudContext.getSpendProfile(),
            dbCloudContext.getState(),
            dbCloudContext.getFlightId(),
            dbCloudContext.getError()));
  }
}
