package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterInstanceGroupConfig;
import bio.terra.workspace.generated.model.ApiGcpDataprocClusterLifecycleConfig;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import com.google.api.services.dataproc.model.Cluster;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class CreateDataprocClusterStepTest extends BaseSpringBootUnitTest {

  private static final String SERVICE_ACCOUNT = "test-pet-sa@my-project.iam.gserviceaccount.com";
  private static final List<String> SA_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");
  private static final String WORKSPACE_ID = "my-workspace-ufid";
  private static final String SERVER_ID = "test-server";
  private static final String GIT_BRANCH = "fun-branch";
  private static final ApiGcpDataprocClusterCreationParameters creationParameters =
      new ApiGcpDataprocClusterCreationParameters()
          .clusterId("my-cluster")
          .region("us-central1")
          .components(List.of("JUPYTER"))
          .metadata(Map.of("metadata-key", "metadata-value"))
          .autoscalingPolicy("my-policy")
          .managerNodeConfig(
              new ApiGcpDataprocClusterInstanceGroupConfig()
                  .numInstances(1)
                  .machineType("n2-standard-2"))
          .primaryWorkerConfig(
              new ApiGcpDataprocClusterInstanceGroupConfig()
                  .numInstances(2)
                  .machineType("n2-standard-2"))
          .lifecycleConfig(new ApiGcpDataprocClusterLifecycleConfig().idleDeleteTtl("1800s"));

  @Test
  public void setFields() {
    Cluster cluster =
        CreateDataprocClusterStep.setFields(
            creationParameters.getClusterId(),
            creationParameters,
            "my-staging-bucket",
            "my-temp-bucket",
            SERVICE_ACCOUNT,
            WORKSPACE_ID,
            SERVER_ID,
            new Cluster(),
            GIT_BRANCH);

    Map<String, String> metadata = cluster.getConfig().getGceClusterConfig().getMetadata();
    // Metadata includes: workspaceId, serverId, startup-script-url, enable-guest-attributes, and
    // metadata-key
    assertThat(metadata, Matchers.aMapWithSize(5));
    assertThat(metadata, Matchers.hasEntry("metadata-key", "metadata-value"));
    assertDefaultMetadata(cluster);

    assertEquals(SERVICE_ACCOUNT, cluster.getConfig().getGceClusterConfig().getServiceAccount());
    assertEquals(SA_SCOPES, cluster.getConfig().getGceClusterConfig().getServiceAccountScopes());

    assertEquals(1, cluster.getConfig().getMasterConfig().getNumInstances());
    assertEquals("n2-standard-2", cluster.getConfig().getMasterConfig().getMachineTypeUri());

    assertEquals(2, cluster.getConfig().getWorkerConfig().getNumInstances());
    assertEquals("n2-standard-2", cluster.getConfig().getWorkerConfig().getMachineTypeUri());

    assertEquals("my-policy", cluster.getConfig().getAutoscalingConfig().getPolicyUri());

    assertEquals("1800s", cluster.getConfig().getLifecycleConfig().getIdleDeleteTtl());
  }

  private void assertDefaultMetadata(Cluster cluster) {
    Map<String, String> metadata = cluster.getConfig().getGceClusterConfig().getMetadata();
    assertThat(metadata, Matchers.hasEntry("terra-workspace-id", WORKSPACE_ID));
    assertThat(metadata, Matchers.hasEntry("terra-cli-server", SERVER_ID));
  }

  @Test
  public void setFieldsThrowsForReservedMetadataKeys() {
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateDataprocClusterStep.setFields(
                creationParameters.getClusterId(),
                creationParameters
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-workspace-id", "fakeworkspaceid")),
                "my-staging-bucket",
                "my-temp-bucket",
                SERVICE_ACCOUNT,
                WORKSPACE_ID,
                SERVER_ID,
                new Cluster(),
                GIT_BRANCH));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateDataprocClusterStep.setFields(
                creationParameters.getClusterId(),
                creationParameters
                    // "terra-cli-server" is a reserved metadata key.
                    .metadata(Map.of("terra-cli-server", "fakeserver")),
                "my-staging-bucket",
                "my-temp-bucket",
                SERVICE_ACCOUNT,
                WORKSPACE_ID,
                SERVER_ID,
                new Cluster(),
                GIT_BRANCH));
  }
}
