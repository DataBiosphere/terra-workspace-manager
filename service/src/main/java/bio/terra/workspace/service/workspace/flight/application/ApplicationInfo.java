package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmApplication;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = ApplicationInfoImpl.class)
public interface ApplicationInfo {
  WsmApplication getApplication();

  boolean isApplicationAlreadyCorrect();

  boolean isSamAlreadyCorrect();
}
