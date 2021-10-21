package bio.terra.workspace.service.iam.model;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiControlledResourceIamRole;
import java.util.Arrays;
import java.util.Optional;

/** Internal representation of resource-level IAM roles. */
public enum ControlledResourceIamRole {
  OWNER("owner", null),
  // Only application-private resources have the DELETER role defined in Sam
  DELETER("deleter", null),
  EDITOR("editor", ApiControlledResourceIamRole.EDITOR),
  WRITER("writer", ApiControlledResourceIamRole.WRITER),
  READER("reader", ApiControlledResourceIamRole.READER);

  private final String samRole;
  private final ApiControlledResourceIamRole apiRole;

  ControlledResourceIamRole(String samRole, ApiControlledResourceIamRole apiRole) {
    this.samRole = samRole;
    this.apiRole = apiRole;
  }

  /**
   * @param apiModel incoming API IAM role - cannot be null
   * @return internal model IAM role
   */
  public static ControlledResourceIamRole fromApiModel(ApiControlledResourceIamRole apiModel) {
    if (apiModel == null) {
      throw new InternalLogicException("Do not call fromApiModel with null argument");
    }
    Optional<ControlledResourceIamRole> result =
        Arrays.stream(ControlledResourceIamRole.values())
            .filter(x -> apiModel.equals(x.apiRole))
            .findFirst();
    return result.orElseThrow(
        () -> new ValidationException("Invalid ControlledResourceIamRole specified" + apiModel));
  }

  public static ControlledResourceIamRole fromSamRole(String samRole) {
    Optional<ControlledResourceIamRole> result =
        Arrays.stream(ControlledResourceIamRole.values())
            .filter(x -> x.samRole.equals(samRole))
            .findFirst();
    return result.orElseThrow(
        () ->
            new InternalLogicException(
                "No ControlledResourceIamRole enum found corresponding to Sam role " + samRole));
  }

  public ApiControlledResourceIamRole toApiModel() {
    return apiRole;
  }

  public String toSamRole() {
    return samRole;
  }
}
