package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
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
    Properties properties = new Properties();
    Property property1 = new Property().key("foo").value("bar");
    Property property2 = new Property().key("xyzzy").value("plohg");
    properties.add(property1);
    properties.add(property2);

    WsmPolicyInputs policies =
        new WsmPolicyInputs()
            .addInputsItem(
                new WsmPolicyInput()
                    .name("region-constraint")
                    .namespace("terra")
                    .addAdditionalDataItem(
                        new WsmPolicyPair().key("region-name").value("us-central1")));

    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceUuid)
            .spendProfile(spendProfileId)
            .stage(getStageModel())
            .properties(properties)
            .policies(policies);
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceUuid));
    return workspace;
  }
}
