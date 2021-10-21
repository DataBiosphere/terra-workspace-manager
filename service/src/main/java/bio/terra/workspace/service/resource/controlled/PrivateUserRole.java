package bio.terra.workspace.service.resource.controlled;

import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hold the user email and IAM role for assigning a role to a private controlled resource {@link
 * #present} is true if the user role was specified; userEmail and roles are non-null.
 *
 * <p>false if the user/role was not specified; userEmail and roles are null.
 */
public class PrivateUserRole {
  private final boolean present;
  private final String userEmail;
  private final ControlledResourceIamRole role;

  @JsonCreator
  public PrivateUserRole(
      @JsonProperty boolean present,
      @JsonProperty String userEmail,
      @JsonProperty ControlledResourceIamRole role) {
    this.present = present;
    this.userEmail = userEmail;
    this.role = role;
  }

  public boolean isPresent() {
    return present;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public ControlledResourceIamRole getRole() {
    return role;
  }

  public static class Builder {
    private boolean present;
    private String userEmail;
    private ControlledResourceIamRole role;

    public Builder present(boolean present) {
      this.present = present;
      return this;
    }

    public Builder userEmail(String userEmail) {
      this.userEmail = userEmail;
      return this;
    }

    public Builder role(ControlledResourceIamRole role) {
      this.role = role;
      return this;
    }

    public PrivateUserRole build() {
      if (!present) {
        userEmail = null;
        role = null;
      }

      return new PrivateUserRole(present, userEmail, role);
    }
  }
}
