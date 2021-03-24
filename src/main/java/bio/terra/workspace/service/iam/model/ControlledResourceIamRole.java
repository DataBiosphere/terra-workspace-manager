package bio.terra.workspace.service.iam.model;

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

  public static ControlledResourceIamRole fromApiModel(ApiControlledResourceIamRole apiModel) {
    Optional<ControlledResourceIamRole> result =
        Arrays.stream(ControlledResourceIamRole.values())
            .filter(x -> x.apiRole.equals(apiModel))
            .findFirst();
    return result.orElseThrow(
        () ->
            new RuntimeException(
                "No ControlledResourceIamRole enum found corresponding to model role "
                    + apiModel.toString()));
  }

  public String toSamRole() {
    return samRole;
  }
}
