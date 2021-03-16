package bio.terra.workspace.service.iam.model;

import bio.terra.workspace.generated.model.ApiIamRole;
import java.util.Arrays;
import java.util.Optional;

/** Internal representation of IAM roles. */
public enum WsmIamRole {
  READER("reader", ApiIamRole.READER),
  WRITER("writer", ApiIamRole.WRITER),
  OWNER("owner", ApiIamRole.OWNER);

  private final String samRole;
  private final ApiIamRole apiRole;

  WsmIamRole(String samRole, bio.terra.workspace.generated.model.ApiIamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  public static WsmIamRole fromApiModel(bio.terra.workspace.generated.model.ApiIamRole apiModel) {
    Optional<WsmIamRole> result =
        Arrays.stream(WsmIamRole.values()).filter(x -> x.apiRole.equals(apiModel)).findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No IamRole enum found corresponding to model role " + apiModel.toString()));
  }

  public static WsmIamRole fromSam(String samRole) {
    Optional<WsmIamRole> result =
        Arrays.stream(WsmIamRole.values()).filter(x -> x.samRole.equals(samRole)).findFirst();
    return result.orElseThrow(
        () -> new RuntimeException("No IamRole enum found corresponding to Sam role " + samRole));
  }

  public ApiIamRole toApiModel() {
    return apiRole;
  }

  public String toSamRole() {
    return samRole;
  }
}
