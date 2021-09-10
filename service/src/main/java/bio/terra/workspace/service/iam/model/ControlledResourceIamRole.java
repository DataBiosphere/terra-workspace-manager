package bio.terra.workspace.service.iam.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.generated.model.ApiControlledResourceIamRole;
import java.util.Arrays;
import java.util.Optional;

/**
 * Internal representation of resource-level IAM roles. See {@Code
 * ControlledResourceInheritanceMapping} for the canonical mapping of workspace IamRoles to
 * equivalent ControlledResourceIamRoles.
 */
public enum ControlledResourceIamRole {
  OWNER("owner", null),
  // Only private resources have the ASSIGNER role defined in Sam.
  ASSIGNER("assigner", null),
  EDITOR("editor", ApiControlledResourceIamRole.EDITOR),
  WRITER("writer", ApiControlledResourceIamRole.WRITER),
  READER("reader", ApiControlledResourceIamRole.READER);

  private final String samRole;
  private final ApiControlledResourceIamRole apiRole;

  ControlledResourceIamRole(String samRole, ApiControlledResourceIamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  // Some ControlledResourceIamRole.apiRole fields are null, so the below check needs to be careful
  // not to call .equals on a null role. This also will throw if provided apiModel is null.
  public static ControlledResourceIamRole fromApiModel(ApiControlledResourceIamRole apiModel) {
    if (apiModel == null) {
      throw new ValidationException("Non-null ApiControlledResourceIamRole required.");
    }
    Optional<ControlledResourceIamRole> result =
        Arrays.stream(ControlledResourceIamRole.values())
            .filter(x -> apiModel.equals(x.apiRole))
            .findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No ControlledResourceIamRole enum found corresponding to model role "
                    + apiModel.toString()));
  }

  public static ControlledResourceIamRole fromSamRole(String samRole) {
    Optional<ControlledResourceIamRole> result =
        Arrays.stream(ControlledResourceIamRole.values())
            .filter(x -> x.samRole.equals(samRole))
            .findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No ControlledResourceIamRole enum found corresponding to Sam role " + samRole));
  }

  public String toSamRole() {
    return samRole;
  }
}
