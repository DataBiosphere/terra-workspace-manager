package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.WorkspaceStageModel;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;

/**
 * Internal representation of the workspace stage used for gating MC Terra features from Rawls
 * workspaces.
 */
public enum WorkspaceStage {
  RAWLS_WORKSPACE,
  MC_WORKSPACE;

  private static final BiMap<WorkspaceStage, WorkspaceStageModel> stageMap =
      EnumBiMap.create(WorkspaceStage.class, WorkspaceStageModel.class);

  static {
    stageMap.put(WorkspaceStage.RAWLS_WORKSPACE, WorkspaceStageModel.RAWLS_WORKSPACE);
    stageMap.put(WorkspaceStage.MC_WORKSPACE, WorkspaceStageModel.MC_WORKSPACE);
  }

  public static WorkspaceStage fromApiModel(WorkspaceStageModel modelEnum) {
    return stageMap.inverse().get(modelEnum);
  }

  public WorkspaceStageModel toApiModel() {
    return stageMap.get(this);
  }
}
