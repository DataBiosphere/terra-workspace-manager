package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;

/**
 * Internal representation of the workspace stage used for gating MC Terra features from Rawls
 * workspaces.
 */
public enum WorkspaceStage {
  RAWLS_WORKSPACE,
  MC_WORKSPACE;

  private static final BiMap<WorkspaceStage, ApiWorkspaceStageModel> stageMap =
      EnumBiMap.create(WorkspaceStage.class, ApiWorkspaceStageModel.class);

  static {
    stageMap.put(WorkspaceStage.RAWLS_WORKSPACE, ApiWorkspaceStageModel.RAWLS_WORKSPACE);
    stageMap.put(WorkspaceStage.MC_WORKSPACE, ApiWorkspaceStageModel.MC_WORKSPACE);
  }

  public static WorkspaceStage fromApiModel(ApiWorkspaceStageModel modelEnum) {
    return stageMap.inverse().get(modelEnum);
  }

  public ApiWorkspaceStageModel toApiModel() {
    return stageMap.get(this);
  }
}
