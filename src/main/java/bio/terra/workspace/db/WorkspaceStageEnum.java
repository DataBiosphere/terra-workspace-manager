package bio.terra.workspace.db;

import bio.terra.workspace.db.exception.InvalidWorkspaceStageException;
import bio.terra.workspace.generated.model.WorkspaceStageEnumModel;

/**
 * Internal representation of the workspace stage used for gating MC Terra features from Rawls
 * workspaces. This is the enum serialized into the Workspace db table, not the enum generated as
 * part of the external API. Current values RAWLS_WORKSPACE or MC_WORKSPACE.
 */
public enum WorkspaceStageEnum {
  RAWLS_WORKSPACE("RAWLS_WORKSPACE_INTERNAL"),
  MC_WORKSPACE("MC_WORKSPACE_INTERNAL");

  private String value;

  WorkspaceStageEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  public static WorkspaceStageEnum fromValue(String value) {
    for (WorkspaceStageEnum b : WorkspaceStageEnum.values()) {
      if (String.valueOf(b.value).equals(value)) {
        return b;
      }
    }
    return null;
  }

  public static WorkspaceStageEnum fromApiModel(WorkspaceStageEnumModel modelEnum) {
    switch (modelEnum) {
      case RAWLS_WORKSPACE:
        return WorkspaceStageEnum.RAWLS_WORKSPACE;
      case MC_WORKSPACE:
        return WorkspaceStageEnum.MC_WORKSPACE;
      default:
        throw new InvalidWorkspaceStageException(
            "Invalid workspace stage provided: " + modelEnum.toString());
    }
  }

  public static WorkspaceStageEnumModel toApiModel(WorkspaceStageEnum internalEnum) {
    switch (internalEnum) {
      case RAWLS_WORKSPACE:
        return WorkspaceStageEnumModel.RAWLS_WORKSPACE;
      case MC_WORKSPACE:
        return WorkspaceStageEnumModel.MC_WORKSPACE;
      default:
        throw new InvalidWorkspaceStageException(
            "Invalid workspace stage provided: " + internalEnum.toString());
    }
  }
}
