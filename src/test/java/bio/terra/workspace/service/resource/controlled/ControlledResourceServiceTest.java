package bio.terra.workspace.service.resource.controlled;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.google.api.services.notebooks.v1.model.Instance;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

public class ControlledResourceServiceTest extends BaseConnectedTest {
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private CrlService crlService;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceConnectedTestUtils workspaceUtils;

  @Test
  @DisabledIfEnvironmentVariable(named = "TEST_ENV", matches = bufferServiceDisabledEnvsRegEx)
  public void createAiNotebookInstance() throws Exception {
    Workspace workspace =
        workspaceUtils.createWorkspaceWithGcpContext(userAccessUtils.defaultUserAuthRequest());

    String instanceId = "create-ai-notebook-instance";
    String location = "us-east1-b";

    ApiGcpAiNotebookInstanceCreationParameters creationParameters =
        ControlledResourceFixtures.defaultNotebookCreationParameters()
            .instanceId(instanceId)
            .location(location);
    ControlledAiNotebookInstanceResource resource =
        ControlledResourceFixtures.makeDefaultAiNotebookInstance()
            .workspaceId(workspace.getWorkspaceId())
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .assignedUser(userAccessUtils.getDefaultUserEmail())
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .instanceId(instanceId)
            .location(location)
            .build();

    String jobId =
        controlledResourceService.createAiNotebookInstance(
            resource,
            creationParameters,
            List.of(
                ControlledResourceIamRole.OWNER,
                ControlledResourceIamRole.WRITER,
                ControlledResourceIamRole.EDITOR),
            UUID.randomUUID().toString(),
            "fakeResultPath",
            userAccessUtils.defaultUserAuthRequest());
    jobService.waitForJob(jobId);

    AIPlatformNotebooksCow notebooks = crlService.getAIPlatformNotebooksCow();
    Instance instance =
        notebooks
            .instances()
            .get(
                InstanceName.builder()
                    .projectId(workspace.getGcpCloudContext().get().getGcpProjectId())
                    .location(location)
                    .instanceId(instanceId)
                    .build())
            .execute();

    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy_mode", "service_account"));
    // TODO test user has permission on service account.

    assertEquals(
        resource,
        controlledResourceService.getControlledResource(
            workspace.getWorkspaceId(),
            resource.getResourceId(),
            userAccessUtils.defaultUserAuthRequest()));
  }
}
