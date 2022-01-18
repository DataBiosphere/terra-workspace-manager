package scripts.testscripts;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceRequest;
import bio.terra.workspace.model.DeleteControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.GcpAiNotebookInstanceResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import com.google.api.services.notebooks.v1.model.StopInstanceRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.ResourceModifier;
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
    assertNotEquals(
        resourceUser.userEmail, otherWorkspaceUser.userEmail, "The two test users are distinct");
    this.instanceId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
  }

  @Override
  @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE")
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

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
        ResourceMaker.makeControlledNotebookUserPrivate(
            getWorkspaceId(), instanceId, /*location=*/null, resourceUserApi);

    UUID resourceId = creationResult.getAiNotebookInstance().getMetadata().getResourceId();

    GcpAiNotebookInstanceResource resource =
        resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId);
    assertEquals(
        instanceId,
        resource.getAttributes().getInstanceId(),
        "Notebook instance id is correct in GET response from WSM");
    assertEquals(
        instanceId,
        creationResult.getAiNotebookInstance().getAttributes().getInstanceId(),
        "Notebook instance id is correct in create response from WSM");
    assertEquals(
        resourceUser.userEmail,
        resource
            .getMetadata()
            .getControlledResourceMetadata()
            .getPrivateResourceUser()
            .getUserName(),
        "User is the private user of the notebook");
    assertEquals(
        "us-central1-a",
        resource.getAttributes().getLocation(),
        "When location is not set, use the default location"
    );

    createAControlledAiNotebookInstanceWithoutSpecifiedInstanceId_validInstanceIdIsGenerated(
        resourceUserApi);

    createAControlledAiNotebookInstanceWithLocationSpecified(resourceUserApi);
    
    String instanceName =
        String.format(
            "projects/%s/locations/%s/instances/%s",
            resource.getAttributes().getProjectId(),
            resource.getAttributes().getLocation(),
            resource.getAttributes().getInstanceId());
    AIPlatformNotebooks userNotebooks = ClientTestUtils.getAIPlatformNotebooksClient(resourceUser);

    assertTrue(
        ResourceModifier.userHasProxyAccess(
            creationResult, resourceUser, resource.getAttributes().getProjectId()),
        "Private resource user has access to their notebook");
    assertFalse(
        ResourceModifier.userHasProxyAccess(
            creationResult, otherWorkspaceUser, resource.getAttributes().getProjectId()),
        "Other workspace user does not have access to a private notebook");

    // The user should be able to stop their notebook.
    userNotebooks.projects().locations().instances().stop(instanceName, new StopInstanceRequest());

    // The user should not be able to directly delete their notebook.
    GoogleJsonResponseException directDeleteForbidden =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> userNotebooks.projects().locations().instances().delete(instanceName).execute());
    assertEquals(
        HttpStatus.SC_FORBIDDEN,
        directDeleteForbidden.getStatusCode(),
        "User may not delete notebook directly on GCP");

    // Delete the AI Notebook through WSM.
    DeleteControlledGcpAiNotebookInstanceResult deleteResult =
        resourceUserApi.deleteAiNotebookInstance(
            new DeleteControlledGcpAiNotebookInstanceRequest()
                .jobControl(new JobControl().id(UUID.randomUUID().toString())),
            getWorkspaceId(),
            resourceId);
    String deleteJobId = deleteResult.getJobReport().getId();
    deleteResult =
        ClientTestUtils.pollWhileRunning(
            deleteResult,
            () -> resourceUserApi.getDeleteAiNotebookInstanceResult(getWorkspaceId(), deleteJobId),
            DeleteControlledGcpAiNotebookInstanceResult::getJobReport,
            Duration.ofSeconds(10));

    ClientTestUtils.assertJobSuccess(
        "delete ai notebook", deleteResult.getJobReport(), deleteResult.getErrorReport());

    // Verify the notebook was deleted from WSM metadata.
    ApiException notebookIsMissing =
        assertThrows(
            ApiException.class,
            () -> resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId),
            "Notebook is deleted from WSM");
    assertEquals(HttpStatus.SC_NOT_FOUND, notebookIsMissing.getCode(), "Error from WSM is 404");
    // Verify the notebook was deleted from GCP.
    GoogleJsonResponseException notebookNotFound =
        assertThrows(
            GoogleJsonResponseException.class,
            () -> userNotebooks.projects().locations().instances().get(instanceName).execute(),
            "Notebook is deleted from GCP");
    // GCP may respond with either 403 or 404 depending on how quickly this is called after deleting
    // the notebook. Either response is valid in this case.
    assertThat(
        "Error from GCP is 403 or 404",
        notebookNotFound.getStatusCode(),
        anyOf(equalTo(HttpStatus.SC_NOT_FOUND), equalTo(HttpStatus.SC_FORBIDDEN)));
  }

  private void
      createAControlledAiNotebookInstanceWithoutSpecifiedInstanceId_validInstanceIdIsGenerated(
          ControlledGcpResourceApi resourceUserApi) throws ApiException, InterruptedException {
    CreatedControlledGcpAiNotebookInstanceResult resourceWithNotebookInstanceIdNotSpecified =
        ResourceMaker.makeControlledNotebookUserPrivate(
            getWorkspaceId(), /*instanceId=*/ null, /*location=*/null, resourceUserApi);
    assertNotNull(
        resourceWithNotebookInstanceIdNotSpecified
            .getAiNotebookInstance()
            .getAttributes()
            .getInstanceId());
    resourceUserApi.deleteAiNotebookInstance(
        new DeleteControlledGcpAiNotebookInstanceRequest()
            .jobControl(new JobControl().id(UUID.randomUUID().toString())),
        getWorkspaceId(),
        resourceWithNotebookInstanceIdNotSpecified
            .getAiNotebookInstance()
            .getMetadata()
            .getResourceId());
  }

  private void
  createAControlledAiNotebookInstanceWithLocationSpecified(
      ControlledGcpResourceApi resourceUserApi) throws ApiException, InterruptedException {
    String location = "us-east1-a";
    CreatedControlledGcpAiNotebookInstanceResult resourceWithSpecifiedLocation =
        ResourceMaker.makeControlledNotebookUserPrivate(
            getWorkspaceId(), /*instanceId=*/ null, location, resourceUserApi);
    assertEquals(location,
        resourceWithSpecifiedLocation.getAiNotebookInstance().getAttributes().getLocation());
    resourceUserApi.deleteAiNotebookInstance(
        new DeleteControlledGcpAiNotebookInstanceRequest()
            .jobControl(new JobControl().id(UUID.randomUUID().toString())),
        getWorkspaceId(),
        resourceWithSpecifiedLocation
            .getAiNotebookInstance()
            .getMetadata()
            .getResourceId());
  }
}
