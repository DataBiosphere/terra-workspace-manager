package bio.terra.workspace.service.iam.model;

import com.google.auto.value.AutoValue;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** A binding between an IAM role and users/groups. */
@AutoValue
public abstract class RoleBinding {

  /** The role granted to each of the bound users. */
  public abstract IamRole role();

  /** The list of users and/or groups this binding applies to as email addresses. */
  public abstract List<String> users();

  public static RoleBinding.@NotNull Builder builder() {
    return new AutoValue_RoleBinding.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract RoleBinding.Builder role(IamRole value);

    public abstract RoleBinding.Builder users(List<String> value);

    public abstract @NotNull RoleBinding build();
  }
}
