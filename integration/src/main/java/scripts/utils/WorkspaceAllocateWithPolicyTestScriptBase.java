package scripts.utils;

import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WsmPolicyInput;
import bio.terra.workspace.model.WsmPolicyInputs;
import bio.terra.workspace.model.WsmPolicyPair;
import java.util.UUID;

// TODO(PF-1948): This script only exists because environments above dev do not support policies
//  being included on workspaces yet. Once these environments support TPS, this class should be
//  deleted and its policy functionality should be merged into WorkspaceAllocateTestScriptBase.
public abstract class WorkspaceAllocateWithPolicyTestScriptBase
    extends WorkspaceAllocateTestScriptBase {

  @Override
  protected CreatedWorkspace createWorkspace(
      UUID workspaceUuid, String spendProfileId, WorkspaceApi workspaceApi) throws Exception {
    WsmPolicyInputs policies =
        new WsmPolicyInputs()
            .addInputsItem(
                new WsmPolicyInput()
                    .name("region-constraint")
                    .namespace("terra")
                    .addAdditionalDataItem(
                        new WsmPolicyPair().key("region-name").value("us-central1")));

    return createWorkspaceWithPolicy(workspaceUuid, spendProfileId, workspaceApi, policies);
  }
}
