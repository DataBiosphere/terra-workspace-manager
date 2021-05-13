package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.GcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.model.GcpAiNotebookInstanceResource;
import bio.terra.workspace.model.GcpAiNotebookInstanceVmImage;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.StopInstanceRequest;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledAiNotebookInstanceLifecycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger =
      LoggerFactory.getLogger(PrivateControlledAiNotebookInstanceLifecycle.class);

  private TestUserSpecification resourceUser;
  private TestUserSpecification otherWorkspaceUser;
  private String instanceId;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, set up in the super class and passed as the
    // 'testUser'.
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    this.resourceUser = testUsers.get(1);
    this.otherWorkspaceUser = testUsers.get(2);
    assertNotEquals(resourceUser.userEmail, otherWorkspaceUser.userEmail);
    this.instanceId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(resourceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(otherWorkspaceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);

    ControlledGcpResourceApi resourceUserApi =
        ClientTestUtils.getControlledGcpResourceClient(resourceUser, server);
    CreatedControlledGcpAiNotebookInstanceResult creationResult =
        createPrivateNotebook(resourceUser, resourceUserApi);
    String creationJobId = creationResult.getJobReport().getId();
    creationResult = ClientTestUtils.pollWhileRunning(
        creationResult,
        () -> resourceUserApi.getCreateAiNotebookInstanceResult(getWorkspaceId(), creationJobId),
        CreatedControlledGcpAiNotebookInstanceResult::getJobReport,
        Duration.ofSeconds(10));
    assertNull(creationResult.getErrorReport());
    assertEquals(JobReport.StatusEnum.SUCCEEDED, creationResult.getJobReport().getStatus());
    logger.info(
        "Creation succeeded for instanceId {}",
        creationResult.getAiNotebookInstance().getAttributes().getInstanceId());

    UUID resourceId = creationResult.getAiNotebookInstance().getMetadata().getResourceId();

    GcpAiNotebookInstanceResource resource =
        resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId);
    assertEquals(instanceId, resource.getAttributes().getInstanceId());
    assertEquals(instanceId,
        creationResult.getAiNotebookInstance().getAttributes().getInstanceId());

    String instanceName = String
        .format("projects/%s/locations/%s/instances/%s", resource.getAttributes().getProjectId(),
            resource.getAttributes().getLocation(), resource.getAttributes().getInstanceId());
    AIPlatformNotebooks userNotebooks = ClientTestUtils.getAIPlatformNotebooksClient(resourceUser);

    Instance instance = userNotebooks.projects().locations().instances().get(instanceName)
        .execute();

    // TODO(PF-765): Assert that the other user does not have proxy access once we don't have
    // project level service account permissions.
    assertUserHasProxyAccess(resourceUser, instance, resource.getAttributes().getProjectId());
    // The user should be able to stop their notebook.
    userNotebooks.projects().locations().instances().stop(instanceName, new StopInstanceRequest());

    // The user should not be able to directly delete their notebook.
    GoogleJsonResponseException directDeleteForbidden = assertThrows(
        GoogleJsonResponseException.class, () ->
            userNotebooks.projects().locations().instances().delete(instanceName).execute());
    assertEquals(HttpStatus.SC_FORBIDDEN, directDeleteForbidden.getStatusCode());

    // Delete the AI Notebook through WSM.
    DeleteControlledGcpAiNotebookInstanceResult deleteResult = resourceUserApi
        .deleteAiNotebookInstance(new DeleteControlledGcpAiNotebookInstanceRequest()
                .jobControl(new JobControl().id(UUID.randomUUID().toString())), getWorkspaceId(),
            resourceId);
    String deleteJobId = deleteResult.getJobReport().getId();
    deleteResult = ClientTestUtils.pollWhileRunning(deleteResult,
        () -> resourceUserApi.getDeleteAiNotebookInstanceResult(getWorkspaceId(), deleteJobId),
        DeleteControlledGcpAiNotebookInstanceResult::getJobReport,
        Duration.ofSeconds(10));

    assertNull(deleteResult.getErrorReport());
    assertEquals(JobReport.StatusEnum.SUCCEEDED, deleteResult.getJobReport().getStatus());

    // Verify the notebook was deleted from WSM metadata.
    ApiException notebookIsMissing =
        assertThrows(
            ApiException.class,
            () -> resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId));
    assertEquals(HttpStatus.SC_NOT_FOUND, notebookIsMissing.getCode());

    // Verify the notebook was deleted form GCP.
    GoogleJsonResponseException notebookNotFound =
        assertThrows(GoogleJsonResponseException.class, () ->
            userNotebooks.projects().locations().instances().get(instanceName).execute());
    assertEquals(HttpStatus.SC_NOT_FOUND, notebookNotFound.getStatusCode());
  }

  private CreatedControlledGcpAiNotebookInstanceResult createPrivateNotebook(
      TestUserSpecification user, ControlledGcpResourceApi resourceApi)
      throws ApiException, InterruptedException {
    // Fill out the minimum required fields to arbitrary values.
    var creationParameters =
        new GcpAiNotebookInstanceCreationParameters()
            .instanceId(instanceId)
            .location("us-east1-b")
            .machineType("e2-standard-2")
            .vmImage(
                new GcpAiNotebookInstanceVmImage()
                    .projectId("deeplearning-platform-release")
                    .imageFamily("r-latest-cpu-experimental"));

    PrivateResourceIamRoles privateIamRoles = new PrivateResourceIamRoles();
    privateIamRoles.add(ControlledResourceIamRole.EDITOR);
    privateIamRoles.add(ControlledResourceIamRole.WRITER);
    var commonParameters =
        new ControlledResourceCommonFields()
            .name(RandomStringUtils.randomAlphabetic(6))
            .cloningInstructions(CloningInstructionsEnum.NOTHING)
            .accessScope(AccessScope.PRIVATE_ACCESS)
            .managedBy(ManagedBy.USER)
            .privateResourceUser(
                new PrivateResourceUser()
                    .userName(user.userEmail)
                    .privateResourceIamRoles(privateIamRoles));

    var body =
        new CreateControlledGcpAiNotebookInstanceRequestBody()
            .aiNotebookInstance(creationParameters)
            .common(commonParameters)
            .jobControl(new JobControl().id(UUID.randomUUID().toString()));

    return resourceApi.createAiNotebookInstance(body, getWorkspaceId());
  }

  /**
   * Assert that the user has access to the Notebook through the proxy with a service account.
   * <p>We can't directly test that we can go through the proxy to the Jupyter notebook without a
   * real Google user auth flow, so we check the necessary ingredients instead.
   */
  private static void assertUserHasProxyAccess(TestUserSpecification user, Instance instance,
      String projectId) throws GeneralSecurityException, IOException {
    // Test that the user has access to the notebook with a service account through proxy mode.
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));

    // The user needs to have the actAs permission on the service account.
    String actAsPermission = "iam.serviceAccounts.actAs";
    String serviceAccountName = String
        .format("projects/%s/serviceAccounts/%s", projectId, instance.getServiceAccount());
    assertThat(ClientTestUtils.getGcpIamClient(user)
            .projects()
            .serviceAccounts()
            .testIamPermissions(
                serviceAccountName,
                new TestIamPermissionsRequest().setPermissions(List.of(actAsPermission)))
            .execute()
            .getPermissions(),
        Matchers.contains(actAsPermission));
  }
}
