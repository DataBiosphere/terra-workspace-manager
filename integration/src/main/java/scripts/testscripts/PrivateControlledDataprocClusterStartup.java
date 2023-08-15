package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.CreatedControlledGcpDataprocClusterResult;
import bio.terra.workspace.model.GcpDataprocClusterResource;
import com.google.api.services.dataproc.Dataproc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataprocUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledDataprocClusterStartup extends WorkspaceAllocateTestScriptBase {
  private static final String CLUSTER_ID = RandomStringUtils.randomAlphabetic(8).toLowerCase();
  private TestUserSpecification resourceUser;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // We use the workspace owner as the resource user to take advantage of temporary grants giving
    // the user pet SA the needed permissions to create the dataproc cluster.
    this.resourceUser = testUsers.get(0);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // Create project
    String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    // Create dataproc cluster
    ControlledGcpResourceApi resourceUserApi =
        ClientTestUtils.getControlledGcpResourceClient(resourceUser, server);
    String localBranch = System.getenv("TEST_LOCAL_BRANCH");
    String startupScriptUrl =
        StringUtils.isEmpty(localBranch)
            ? null
            : String.format(
                "https://raw.githubusercontent.com/DataBiosphere/terra-workspace-manager/%s/src/main/resources/scripts/dataproc/startup.sh",
                localBranch);
    CreatedControlledGcpDataprocClusterResult creationResult =
        DataprocUtils.createPrivateDataprocCluster(
            getWorkspaceId(), CLUSTER_ID, /* region= */ null, startupScriptUrl, resourceUserApi);

    UUID resourceId = creationResult.getDataprocCluster().getMetadata().getResourceId();
    GcpDataprocClusterResource resource =
        resourceUserApi.getDataprocCluster(getWorkspaceId(), resourceId);

    Dataproc dataproc = ClientTestUtils.getDataprocClient(resourceUser);
    String region = resource.getAttributes().getRegion();

    // Ensure the cluster has the jupyter component url set
    DataprocUtils.assertClusterHasProxyUrl(dataproc, projectId, region, CLUSTER_ID);

    // Ensure the cluster has properly passed in a startup script
    Map<String, String> metadata =
        dataproc
            .projects()
            .regions()
            .clusters()
            .get(projectId, region, CLUSTER_ID)
            .execute()
            .getConfig()
            .getGceClusterConfig()
            .getMetadata();
    assertEquals(startupScriptUrl, metadata.get("startup-script-url"));

    // TODO: Add check that the startup script executed successfully. Pending CLI support to set
    // startup test value metadata.
  }
}
