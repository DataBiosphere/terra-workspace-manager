package bio.terra.workspace.service.iam.model;

import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Internal representation of IAM roles. */
public enum IamRole {
  READER("reader", bio.terra.workspace.generated.model.IamRole.READER),
  WRITER("writer", bio.terra.workspace.generated.model.IamRole.WRITER),
  OWNER("owner", bio.terra.workspace.generated.model.IamRole.OWNER);

  private final String samRole;
  private final bio.terra.workspace.generated.model.IamRole apiRole;

  IamRole(String samRole, bio.terra.workspace.generated.model.IamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  public static IamRole fromApiModel(
      bio.terra.workspace.generated.model.@NotNull IamRole apiModel) {
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

  public bio.terra.workspace.generated.model.IamRole toApiModel() {
    return apiRole;
  }

  public String toSamRole() {
    return samRole;
  }
}
