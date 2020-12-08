package bio.terra.workspace.service.iam.model;

import com.google.auto.value.AutoValue;
import java.util.List;

/** Representation of an IAM binding between a role and users/groups. */
@AutoValue
public abstract class PolicyBinding {

  /** The role granted to each of the bound users. */
  public abstract IamRole role();

  /** The list of users this binding applies to. */
  public abstract List<String> users();

  public static PolicyBinding.Builder builder() {
    return new AutoValue_PolicyBinding.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract PolicyBinding.Builder role(IamRole value);

    public abstract PolicyBinding.Builder users(List<String> value);

    public abstract PolicyBinding build();
  }
}
