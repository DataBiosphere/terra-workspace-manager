package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.Objects;

public class ApplicationAbleResultImpl implements ApplicationAbleResult {

  private final WsmWorkspaceApplication application;

  public ApplicationAbleResultImpl() {
    this.application = null;
  }

  public ApplicationAbleResultImpl(WsmWorkspaceApplication application) {
    this.application = application;
  }

  @Override
  public WsmWorkspaceApplication application() {
    return application;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (ApplicationAbleResultImpl) obj;
    return Objects.equals(this.application, that.application);
  }

  @Override
  public int hashCode() {
    return Objects.hash(application);
  }

  @Override
  public String toString() {
    return "ApplicationAbleResultImpl[" + "application=" + application + ']';
  }
}
