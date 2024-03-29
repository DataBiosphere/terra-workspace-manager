package bio.terra.workspace.service.workspace.flight.application;

import bio.terra.workspace.service.workspace.model.WsmApplication;

public record ApplicationInfoImpl(
    WsmApplication application, boolean applicationAlreadyCorrect, boolean samAlreadyCorrect)
    implements ApplicationInfo {}
