package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.GcpAiNotebookInstanceResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import com.google.api.services.notebooks.v1.AIPlatformNotebooks;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.NotebookUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledAiNotebookInstancePostStartup
    extends WorkspaceAllocateTestScriptBase {
  private static final String INSTANCE_ID = RandomStringUtils.randomAlphabetic(8).toLowerCase();
  // NOTE: ONLY set this to your branch name for local testing purpose.
  private static final String LOCAL_BRANCH = "";
  private TestUserSpecification resourceUser;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    this.resourceUser = testUsers.get(1);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    workspaceApi.grantRole(
        new GrantRoleRequestBody().memberEmail(resourceUser.userEmail),
        getWorkspaceId(),
        IamRole.WRITER);
    ControlledGcpResourceApi resourceUserApi =
        ClientTestUtils.getControlledGcpResourceClient(resourceUser, server);
    String testValue = RandomStringUtils.random(5, /*letters=*/ true, /*numbers=*/ true);
    CreatedControlledGcpAiNotebookInstanceResult creationResult =
        NotebookUtils.makeControlledNotebookUserPrivate(
            getWorkspaceId(),
            INSTANCE_ID,
            /*location=*/ null,
            resourceUserApi,
            testValue,
            /*postStartupScript=*/ StringUtils.isEmpty(LOCAL_BRANCH)
                ? null
                : String.format(
                    "https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/%s/service/src/main/java/bio/terra/workspace/service/resource/controlled/cloud/gcp/ainotebook/post-startup.sh",
                    LOCAL_BRANCH));

    AIPlatformNotebooks userNotebooks = ClientTestUtils.getAIPlatformNotebooksClient(resourceUser);
    var resource = getNotebookResource(resourceUserApi, creationResult);
    var instanceName = composeInstanceName(resource);
    String proxyUrl =
        ClientTestUtils.getWithRetryOnException(
            () -> {
              try {
                String p =
                    userNotebooks
                        .projects()
                        .locations()
                        .instances()
                        .get(instanceName)
                        .execute()
                        .getProxyUri();
                if (p == null) {
                  throw new NullPointerException();
                }
                return p;
                // Do not retry if it's an IO exception.
              } catch (IOException ignored) {
              }
              return null;
            });
    assertNotNull(proxyUrl);
    Map<String, String> metadata =
        userNotebooks.projects().locations().instances().get(instanceName).execute().getMetadata();
    assertEquals(testValue, metadata.get("terra-test-value"));
    assertEquals(
        resource.getMetadata().getName(), metadata.get("terra-gcp-notebook-resource-name"));
    var testResultValue =
        ClientTestUtils.getWithRetryOnException(
            () -> {
              String result =
                  userNotebooks
                      .projects()
                      .locations()
                      .instances()
                      .get(instanceName)
                      .execute()
                      .getMetadata()
                      .get("terra-test-result");
              if (result == null) {
                throw new NullPointerException();
              }
              return result;
            });
    assertEquals(testValue, testResultValue);
  }

  private GcpAiNotebookInstanceResource getNotebookResource(
      ControlledGcpResourceApi resourceUserApi,
      CreatedControlledGcpAiNotebookInstanceResult creationResult)
      throws ApiException {
    UUID resourceId = creationResult.getAiNotebookInstance().getMetadata().getResourceId();

    return resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId);
  }

  private String composeInstanceName(GcpAiNotebookInstanceResource resource) {
    String instanceName =
        String.format(
            "projects/%s/locations/%s/instances/%s",
            resource.getAttributes().getProjectId(),
            resource.getAttributes().getLocation(),
            resource.getAttributes().getInstanceId());
    return instanceName;
  }
}
