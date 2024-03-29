package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmApplication;

public interface ApplicationInfo {
  WsmApplication application();

  boolean applicationAlreadyCorrect();

  boolean samAlreadyCorrect();
}
