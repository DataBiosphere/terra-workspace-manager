package bio.terra.workspace.service.policy.model;

import bio.terra.policy.model.TpsObjectType;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiWsmPolicyObjectType;

public enum PolicyObjectType {
  WORKSPACE(ApiWsmPolicyObjectType.WORKSPACE, TpsObjectType.WORKSPACE);

  private final ApiWsmPolicyObjectType apiWsmPolicyObjectType;
  private final TpsObjectType tpsObjectType;

  PolicyObjectType(ApiWsmPolicyObjectType apiWsmType, TpsObjectType tpsType) {
    this.apiWsmPolicyObjectType = apiWsmType;
    this.tpsObjectType = tpsType;
  }

  public ApiWsmPolicyObjectType toApi() {
    return apiWsmPolicyObjectType;
  }

  public static PolicyObjectType fromTpsObjectType(TpsObjectType tpsObjectType) {
    for (var object : values()) {
      if (object.tpsObjectType.equals(tpsObjectType)) {
        return object;
      }
    }
    throw new InternalLogicException(
        String.format(
            "Do not recognize Tps object type %s: check if new enums should be updated.",
            tpsObjectType.name()));
  }
}
