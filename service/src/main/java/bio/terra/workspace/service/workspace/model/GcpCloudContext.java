package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.generated.model.ApiGcpContext;
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
    return contextFields.getGcpProjectId();
  }

  @JsonIgnore
  public String getSamPolicyOwner() {
    return contextFields.getSamPolicyOwner();
  }

  @JsonIgnore
  public String getSamPolicyWriter() {
    return contextFields.getSamPolicyWriter();
  }

  @JsonIgnore
  public String getSamPolicyReader() {
    return contextFields.getSamPolicyReader();
  }

  @JsonIgnore
  public String getSamPolicyApplication() {
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

  public ApiGcpContext toApi() {
    var gcpContext = new ApiGcpContext();
    gcpContext.operationState(commonFields.toApi());
    if (contextFields != null) {
      contextFields.toApi(gcpContext);
    }
    return gcpContext;
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
