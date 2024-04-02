package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmApplication;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ApplicationInfoImpl implements ApplicationInfo {

  private final WsmApplication application;
  private final boolean applicationAlreadyCorrect;
  private final boolean samAlreadyCorrect;

  public ApplicationInfoImpl(
      @JsonProperty("application") WsmApplication application,
      @JsonProperty("applicationAlreadyCorrect") boolean applicationAlreadyCorrect,
      @JsonProperty("samAlreadyCorrect") boolean samAlreadyCorrect) {
    this.application = application;
    this.applicationAlreadyCorrect = applicationAlreadyCorrect;
    this.samAlreadyCorrect = samAlreadyCorrect;
  }

  @Override
  public WsmApplication getApplication() {
    return application;
  }

  @Override
  public boolean isApplicationAlreadyCorrect() {
    return applicationAlreadyCorrect;
  }

  @Override
  public boolean isSamAlreadyCorrect() {
    return samAlreadyCorrect;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (ApplicationInfoImpl) obj;
    return Objects.equals(this.application, that.application)
        && this.applicationAlreadyCorrect == that.applicationAlreadyCorrect
        && this.samAlreadyCorrect == that.samAlreadyCorrect;
  }

  @Override
  public int hashCode() {
    return Objects.hash(application, applicationAlreadyCorrect, samAlreadyCorrect);
  }

  @Override
  public String toString() {
    return "ApplicationInfoImpl["
        + "application="
        + application
        + ", "
        + "applicationAlreadyCorrect="
        + applicationAlreadyCorrect
        + ", "
        + "samAlreadyCorrect="
        + samAlreadyCorrect
        + ']';
  }
}
