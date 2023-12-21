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
import bio.terra.workspace.api.ResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.ControlledDataprocClusterUpdateParameters;
import bio.terra.workspace.model.CreatedControlledGcpDataprocClusterResult;
import bio.terra.workspace.model.DataprocClusterCloudId;
import bio.terra.workspace.model.GcpDataprocClusterLifecycleConfig;
import bio.terra.workspace.model.GcpDataprocClusterResource;
import bio.terra.workspace.model.GenerateGcpDataprocClusterCloudIdRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceList;
import bio.terra.workspace.model.ResourceType;
import bio.terra.workspace.model.StewardshipType;
import bio.terra.workspace.model.UpdateControlledGcpDataprocClusterRequestBody;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.dataproc.Dataproc;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.StartClusterRequest;
import com.google.api.services.dataproc.model.StopClusterRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.DataprocUtils;
import scripts.utils.MultiResourcesUtils;
import scripts.utils.RetryUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class PrivateControlledDataprocClusterLifeCycle extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger =
      LoggerFactory.getLogger(PrivateControlledDataprocClusterLifeCycle.class);

  private TestUserSpecification resourceUser;
  private TestUserSpecification otherWorkspaceUser;
  private String clusterId;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, set up in the super class and passed as the
    // 'testUser'.
    assertThat(
        "There must be at least three test users defined for this test.",
        testUsers != null && testUsers.size() > 2);
    // We use the workspace owner as the resource user to take advantage of temporary grants giving
    // the user pet SA the needed permissions to create the dataproc cluster.
    this.resourceUser = testUsers.get(0);
    this.otherWorkspaceUser = testUsers.get(2);
    assertNotEquals(
        resourceUser.userEmail, otherWorkspaceUser.userEmail, "The two test users are distinct");
    this.clusterId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
  }

  @Override
  @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE")
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), otherWorkspaceUser, IamRole.WRITER);

    String gcpProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);

    ClientTestUtils.workspaceRoleWaitForPropagation(resourceUser, gcpProjectId);
    ClientTestUtils.workspaceRoleWaitForPropagation(otherWorkspaceUser, gcpProjectId);

    ControlledGcpResourceApi resourceUserApi =
        ClientTestUtils.getControlledGcpResourceClient(resourceUser, server);
    CreatedControlledGcpDataprocClusterResult creationResult =
        DataprocUtils.createPrivateDataprocCluster(
            getWorkspaceId(),
            clusterId,
            /* region= */ null,
            /* startupScriptUrl= */ null,
            resourceUserApi);

    UUID resourceId = creationResult.getDataprocCluster().getMetadata().getResourceId();
    GcpDataprocClusterResource resource =
        resourceUserApi.getDataprocCluster(getWorkspaceId(), resourceId);

    assertEquals(
        clusterId,
        resource.getAttributes().getClusterId(),
        "Dataproc cluster id is correct in GET response from WSM");
    assertEquals(
        clusterId,
        creationResult.getDataprocCluster().getAttributes().getClusterId(),
        "Dataproc cluster id is correct in create response from WSM");
    assertEquals(
        resourceUser.userEmail,
        resource
            .getMetadata()
            .getControlledResourceMetadata()
            .getPrivateResourceUser()
            .getUserName(),
        "User is the private user of the cluster");
    assertEquals(
        "us-central1",
        resource.getAttributes().getRegion(),
        "The cluster uses the default location because location is not specified.");

    GenerateGcpDataprocClusterCloudIdRequestBody dataprocClusterNameRequest =
        new GenerateGcpDataprocClusterCloudIdRequestBody().dataprocClusterName(clusterId);
    DataprocClusterCloudId cloudDataprocClusterName =
        resourceUserApi.generateDataprocClusterCloudId(
            dataprocClusterNameRequest, getWorkspaceId());
    assertEquals(
        cloudDataprocClusterName.getGeneratedDataprocClusterCloudId(), clusterId.toLowerCase());

    // Any workspace user should be able to enumerate clusters, even though they can't
    // read or write them.
    ResourceApi otherUserApi = ClientTestUtils.getResourceClient(otherWorkspaceUser, server);
    ResourceList clusterList =
        otherUserApi.enumerateResources(
            getWorkspaceId(), 0, 5, ResourceType.DATAPROC_CLUSTER, StewardshipType.CONTROLLED);
    List<ResourceDescription> matchCluster =
        clusterList.getResources().stream()
            .filter(
                n ->
                    n.getResourceAttributes()
                        .getGcpDataprocCluster()
                        .getClusterId()
                        .equals(clusterId))
            .toList();
    assertEquals(1, matchCluster.size());
    MultiResourcesUtils.assertResourceType(ResourceType.DATAPROC_CLUSTER, clusterList);

    createControlledDataprocClusterWithoutClusterId_validClusterIdIsGenerated(resourceUserApi);

    String projectId = resource.getAttributes().getProjectId();
    String region = resource.getAttributes().getRegion();
    String clusterId = resource.getAttributes().getClusterId();

    Dataproc dataproc = ClientTestUtils.getDataprocClient(resourceUser);

    DataprocUtils.assertClusterHasProxyUrl(dataproc, projectId, region, clusterId);
    boolean userHasProxyAccess =
        RetryUtils.getWithRetry(
            (hasProxyAccess) -> hasProxyAccess,
            () -> DataprocUtils.userHasProxyAccess(creationResult, resourceUser, projectId),
            "Timed out waiting for resource user to have access to the cluster");
    assertTrue(userHasProxyAccess, "Private resource user has access to their cluster");
    assertFalse(
        DataprocUtils.userHasProxyAccess(creationResult, otherWorkspaceUser, projectId),
        "Other workspace user does not have access to a private cluster");

    // Assert that user has access to the cluster through the Dataproc API.
    Cluster getCluster =
        RetryUtils.getWithRetryOnException(
            () ->
                dataproc
                    .projects()
                    .regions()
                    .clusters()
                    .get(projectId, region, clusterId)
                    .execute());
    assertNotNull(getCluster);

    // The user should not be able to directly delete their cluster.
    GoogleJsonResponseException directDeleteForbidden =
        assertThrows(
            GoogleJsonResponseException.class,
            () ->
                dataproc
                    .projects()
                    .regions()
                    .clusters()
                    .delete(projectId, region, clusterId)
                    .execute());
    assertEquals(
        HttpStatus.SC_FORBIDDEN,
        directDeleteForbidden.getStatusCode(),
        "User may not delete cluster directly on GCP");

    // Update the cluster through WSM.
    var newName = "new-cluster-name";
    var newDescription = "new description for the cluster";
    int newNumPrimaryWorkers = 3;
    int newNumSecondaryWorkers = 0; // Scale secondary workers to 0 so that we can stop the cluster
    GcpDataprocClusterResource updatedResource =
        resourceUserApi.updateDataprocCluster(
            new UpdateControlledGcpDataprocClusterRequestBody()
                .description(newDescription)
                .name(newName)
                .updateParameters(
                    new ControlledDataprocClusterUpdateParameters()
                        .numPrimaryWorkers(newNumPrimaryWorkers)
                        .numSecondaryWorkers(newNumSecondaryWorkers)),
            getWorkspaceId(),
            resourceId);

    assertEquals(newName, updatedResource.getMetadata().getName());
    assertEquals(newDescription, updatedResource.getMetadata().getDescription());
    // Directly fetch the cluster to verify that non wsm managed fields are updated.
    RetryUtils.getWithRetry(
        cluster ->
            newNumPrimaryWorkers == cluster.getConfig().getWorkerConfig().getNumInstances()
                && cluster.getConfig().getSecondaryWorkerConfig() == null
                && cluster.getStatus().getState().equals("RUNNING"),
        () -> dataproc.projects().regions().clusters().get(projectId, region, clusterId).execute(),
        "Timed out waiting for cluster to update");

    // Update the cluster lifecycle rule through WSM. Cluster lifecycle rules cannot be updated in
    // tandem with other parameters, so we update it separately.
    String newIdleDeleteTtl = "1800s";
    resourceUserApi.updateDataprocCluster(
        new UpdateControlledGcpDataprocClusterRequestBody()
            .updateParameters(
                new ControlledDataprocClusterUpdateParameters()
                    .lifecycleConfig(
                        new GcpDataprocClusterLifecycleConfig().idleDeleteTtl(newIdleDeleteTtl))),
        getWorkspaceId(),
        resourceId);

    // Directly fetch the cluster to verify updated lifecycle rules
    RetryUtils.getWithRetry(
        cluster ->
            newIdleDeleteTtl.equals(cluster.getConfig().getLifecycleConfig().getIdleDeleteTtl())
                && cluster.getStatus().getState().equals("RUNNING"),
        () -> dataproc.projects().regions().clusters().get(projectId, region, clusterId).execute(),
        "Timed out waiting for cluster to update");

    // Update the cluster with a bad parameter format
    String badAutoscalingPolicy = "bad-policy";
    ApiException badClusterUpdateParameter =
        assertThrows(
            ApiException.class,
            () ->
                resourceUserApi.updateDataprocCluster(
                    new UpdateControlledGcpDataprocClusterRequestBody()
                        .updateParameters(
                            new ControlledDataprocClusterUpdateParameters()
                                .autoscalingPolicy(badAutoscalingPolicy)),
                    getWorkspaceId(),
                    resourceId),
            "Cluster update with bad parameter format");

    // Verify that WSM throws a bad request exception
    assertThat(
        "WSM throws a bad request exception",
        badClusterUpdateParameter.getCode(),
        equalTo(HttpStatus.SC_BAD_REQUEST));

    // The user should be able to stop their cluster.
    dataproc
        .projects()
        .regions()
        .clusters()
        .stop(projectId, region, clusterId, new StopClusterRequest())
        .execute();

    // Assert cluster is stopped
    RetryUtils.getWithRetry(
        cluster -> cluster.getStatus().getState().equals("STOPPED"),
        () -> dataproc.projects().regions().clusters().get(projectId, region, clusterId).execute(),
        "Timed out waiting for cluster to be stopped");

    // The user should be able to start their cluster.
    dataproc
        .projects()
        .regions()
        .clusters()
        .start(projectId, region, clusterId, new StartClusterRequest())
        .execute();

    // Assert cluster is running
    RetryUtils.getWithRetry(
        cluster -> cluster.getStatus().getState().equals("RUNNING"),
        () -> dataproc.projects().regions().clusters().get(projectId, region, clusterId).execute(),
        "Timed out waiting for cluster to start");

    // Delete the Dataproc cluster through WSM.
    DataprocUtils.deleteControlledDataprocCluster(getWorkspaceId(), resourceId, resourceUserApi);

    // Verify the cluster was deleted from WSM metadata.
    ApiException clusterIsMissing =
        assertThrows(
            ApiException.class,
            () -> resourceUserApi.getDataprocCluster(getWorkspaceId(), resourceId),
            "Cluster is deleted from WSM");
    assertEquals(HttpStatus.SC_NOT_FOUND, clusterIsMissing.getCode(), "Error from WSM is 404");
    // Verify the cluster was deleted from GCP.
    GoogleJsonResponseException clusterNotFound =
        assertThrows(
            GoogleJsonResponseException.class,
            () ->
                dataproc
                    .projects()
                    .regions()
                    .clusters()
                    .get(projectId, region, clusterId)
                    .execute(),
            "Cluster is deleted from GCP");
    // GCP may respond with either 403 or 404 depending on how quickly this is called after deleting
    // the cluster. Either response is valid in this case.
    assertThat(
        "Error from GCP is 403 or 404",
        clusterNotFound.getStatusCode(),
        anyOf(equalTo(HttpStatus.SC_NOT_FOUND), equalTo(HttpStatus.SC_FORBIDDEN)));
  }

  private void createControlledDataprocClusterWithoutClusterId_validClusterIdIsGenerated(
      ControlledGcpResourceApi resourceUserApi) throws Exception {
    CreatedControlledGcpDataprocClusterResult resourceWithoutClusterId =
        DataprocUtils.createPrivateDataprocCluster(
            getWorkspaceId(),
            /* clusterId= */ null,
            /* location= */ null,
            /* startupScriptUrl= */ null,
            resourceUserApi);
    assertNotNull(resourceWithoutClusterId.getDataprocCluster().getAttributes().getClusterId());
    UUID resourceId = resourceWithoutClusterId.getDataprocCluster().getMetadata().getResourceId();
    DataprocUtils.deleteControlledDataprocCluster(getWorkspaceId(), resourceId, resourceUserApi);
  }
}
