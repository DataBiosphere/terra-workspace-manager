package bio.terra.workspace.service.iam.model;

import java.util.List;
import javax.annotation.processing.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_RoleBinding extends RoleBinding {

  private final WsmIamRole role;

  private final List<String> users;

  private AutoValue_RoleBinding(
      WsmIamRole role,
      List<String> users) {
    this.role = role;
    this.users = users;
  }

  @Override
  public WsmIamRole role() {
    return role;
  }

  @Override
  public List<String> users() {
    return users;
  }

  @Override
  public String toString() {
    return "RoleBinding{"
        + "role=" + role + ", "
        + "users=" + users
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof RoleBinding) {
      RoleBinding that = (RoleBinding) o;
      return this.role.equals(that.role())
          && this.users.equals(that.users());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= role.hashCode();
    h$ *= 1000003;
    h$ ^= users.hashCode();
    return h$;
  }

  static final class Builder extends RoleBinding.Builder {
    private WsmIamRole role;
    private List<String> users;
    Builder() {
    }
    @Override
    public RoleBinding.Builder role(WsmIamRole role) {
      if (role == null) {
        throw new NullPointerException("Null role");
      }
      this.role = role;
      return this;
    }
    @Override
    public RoleBinding.Builder users(List<String> users) {
      if (users == null) {
        throw new NullPointerException("Null users");
      }
      this.users = users;
      return this;
    }
    @Override
    public RoleBinding build() {
      String missing = "";
      if (this.role == null) {
        missing += " role";
      }
      if (this.users == null) {
        missing += " users";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_RoleBinding(
          this.role,
          this.users);
    }
  }

}
