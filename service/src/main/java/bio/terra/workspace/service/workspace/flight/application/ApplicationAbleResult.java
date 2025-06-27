package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = ApplicationAbleResultImpl.class)
public interface ApplicationAbleResult {
  WsmWorkspaceApplication getApplication();
}
