package bio.terra.workspace.service.workspace.model;

import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** An instance of this class represents a WSM application. */
public class WsmApplication {
  private UUID applicationId;
  private String displayName;
  private String description;
  private String serviceAccount;
  private WsmApplicationState state;

  public UUID getApplicationId() {
    return applicationId;
  }

  public WsmApplication applicationId(UUID applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public WsmApplication displayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public WsmApplication description(String description) {
    this.description = description;
    return this;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public WsmApplication serviceAccount(String serviceAccount) {
    this.serviceAccount = serviceAccount;
    return this;
  }

  public WsmApplicationState getState() {
    return state;
  }

  public WsmApplication state(WsmApplicationState state) {
    this.state = state;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    WsmApplication that = (WsmApplication) o;

    return new EqualsBuilder()
        .append(applicationId, that.applicationId)
        .append(displayName, that.displayName)
        .append(description, that.description)
        .append(serviceAccount, that.serviceAccount)
        .append(state, that.state)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(applicationId)
        .append(displayName)
        .append(description)
        .append(serviceAccount)
        .append(state)
        .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("applicationId", applicationId)
        .append("displayName", displayName)
        .append("description", description)
        .append("serviceAccount", serviceAccount)
        .append("state", state)
        .toString();
  }
}
