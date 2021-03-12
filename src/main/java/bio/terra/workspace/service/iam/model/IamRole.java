package bio.terra.workspace.service.iam.model;

import bio.terra.workspace.generated.model.ApiIamRole;
import java.util.Arrays;
import java.util.Optional;

/** Internal representation of IAM roles. */
public enum IamRole {
  READER("reader", bio.terra.workspace.generated.model.ApiIamRole.READER),
  WRITER("writer", bio.terra.workspace.generated.model.ApiIamRole.WRITER),
  OWNER("owner", bio.terra.workspace.generated.model.ApiIamRole.OWNER);

  private final String samRole;
  private final ApiIamRole apiRole;

  IamRole(String samRole, bio.terra.workspace.generated.model.ApiIamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  public static IamRole fromApiModel(bio.terra.workspace.generated.model.ApiIamRole apiModel) {
    Optional<IamRole> result =
        Arrays.stream(IamRole.values()).filter(x -> x.apiRole.equals(apiModel)).findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No IamRole enum found corresponding to model role " + apiModel.toString()));
  }

  public static IamRole fromSam(String samRole) {
    Optional<IamRole> result =
        Arrays.stream(IamRole.values()).filter(x -> x.samRole.equals(samRole)).findFirst();
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
