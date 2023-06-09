package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.WsmResourceService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.containerservice.ContainerServiceManager;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("azureConnected")
@TestInstance(Lifecycle.PER_CLASS)
public class AzureCloneWorkspaceTest extends BaseAzureConnectedTest {
  @Autowired private JobService jobService;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private AzureCloudContextService azureCloudContextService;
  @Autowired private ControlledResourceService controlledResourceService;
  @Autowired private WsmResourceService wsmResourceService;
  @Autowired private UserAccessUtils userAccessUtils;

  private Workspace sourceWorkspace = null;
  private Workspace destWorkspace = null;

  //  @BeforeAll
  //  public void setup() throws InterruptedException {
  //    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
  //    sourceWorkspace = createWorkspaceWithCloudContext(workspaceService, userRequest);
  //  }
  //
  //  @AfterAll
  //  void cleanup() {
  //    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();
  //    Optional.ofNullable(sourceWorkspace)
  //        .ifPresent(workspace -> workspaceService.deleteWorkspace(workspace, userRequest));
  //    Optional.ofNullable(destWorkspace)
  //        .ifPresent(workspace -> workspaceService.deleteWorkspace(workspace, userRequest));
  //  }

  @Test
  void cloneAzureWorkspaceWithContainer() {
    AuthenticatedUserRequest userRequest = userAccessUtils.defaultUserAuthRequest();

    UUID containerResourceId = UUID.randomUUID();
    String storageContainerName = ControlledAzureResourceFixtures.uniqueStorageContainerName();
    ControlledAzureStorageContainerResource containerResource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(sourceWorkspace.getWorkspaceId())
                    .resourceId(containerResourceId)
                    .name(storageContainerName)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .iamRole(ControlledResourceIamRole.OWNER)
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .createdByEmail(userRequest.getEmail())
                    .cloningInstructions(CloningInstructions.COPY_RESOURCE)
                    .region(DEFAULT_AZURE_RESOURCE_REGION)
                    .build())
            .storageContainerName(storageContainerName)
            .build();

    controlledResourceService.createControlledResourceSync(
        containerResource,
        ControlledResourceIamRole.OWNER,
        userRequest,
        new ApiAzureStorageContainerCreationParameters()
            .storageContainerName("storageContainerName"));

    UUID destUUID = UUID.randomUUID();

    destWorkspace =
        Workspace.builder()
            .workspaceId(destUUID)
            .userFacingId("a" + destUUID)
            .spendProfileId(azureTestUtils.getSpendProfileId())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .createdByEmail(userRequest.getEmail())
            .build();

    String cloneJobId =
        workspaceService.cloneWorkspace(
            sourceWorkspace,
            userRequest,
            /*location=*/ null,
            /*additionalPolicies=*/ null,
            destWorkspace,
            azureTestUtils.getSpendProfile());
    jobService.waitForJob(cloneJobId);

    assertEquals(workspaceService.getWorkspace(destUUID), destWorkspace);
    assertTrue(
        azureCloudContextService.getAzureCloudContext(destWorkspace.getWorkspaceId()).isPresent());
    assertEquals(
        wsmResourceService
            .enumerateResources(
                destWorkspace.getWorkspaceId(),
                WsmResourceFamily.AZURE_STORAGE_CONTAINER,
                StewardshipType.CONTROLLED,
                0,
                100)
            .size(),
        1);
  }

  @Autowired private SamService samService;
  @Autowired private LandingZoneApiDispatch landingZoneApiDispatch;
  @Autowired private AzureConfiguration azureConfig;

  @Test
  void testK8s() throws ApiException {
    var azureProfile =
        new AzureProfile(
            "fad90753-2022-4456-9b0a-c7e5b934e408",
            "c5f8eca3-f512-48cb-b01f-f19f1af9014c",
            AzureEnvironment.AZURE);
    var containerServiceManager =
        ContainerServiceManager.authenticate(getManagedAppCredentials(), azureProfile);

    //    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    //    UUID landingZoneId = UUID.fromString("75f4d9ce-7aaf-496a-b39e-aa420a6ac26c");
    //    var clusterResource = landingZoneApiDispatch.getSharedKubernetesCluster(bearerToken,
    // landingZoneId).orElseThrow(() -> new RuntimeException("No shared cluster found"));
    final String mrgName = "test-cf3afe3d-579d-4f97-b7fe-c07d5a6bc967";
    final String aksClusterName = "lzd3f6108103145738f161ef0";
    var rawKubeConfig =
        containerServiceManager
            .kubernetesClusters()
            .manager()
            .serviceClient()
            .getManagedClusters()
            .listClusterUserCredentials(mrgName, aksClusterName)
            .kubeconfigs()
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No kubeconfig found"));
    var kubeConfig =
        KubeConfig.loadKubeConfig(
            new InputStreamReader(new ByteArrayInputStream(rawKubeConfig.value())));
    var userToken = kubeConfig.getCredentials().get("token");

    ApiClient client =
        Config.fromToken(kubeConfig.getServer(), userToken)
            .setSslCaCert(
                new ByteArrayInputStream(
                    Base64.getDecoder()
                        .decode(kubeConfig.getCertificateAuthorityData().getBytes())));
    CoreV1Api api = new CoreV1Api(client);
    var shellCommand =
        String.join(
            " && ",
            List.of(
                "apt update",
                "apt -y install curl",
                "curl -sL https://aka.ms/InstallAzureCLIDeb | bash",
                "az login --federated-token \"$(cat $AZURE_FEDERATED_TOKEN_FILE)\" --service-principal -u $AZURE_CLIENT_ID -t $AZURE_TENANT_ID --allow-no-subscriptions",
                "psql \"host=${DB_SERVER_NAME}.postgres.database.azure.com port=5432 dbname=postgres user=${ADMIN_DB_USER_NAME} password=$(az account get-access-token --query accessToken -otsv) sslmode=require\" --command \"CREATE DATABASE ${NEW_DB_NAME};\"",
                "psql \"host=${DB_SERVER_NAME}.postgres.database.azure.com port=5432 dbname=postgres user=${ADMIN_DB_USER_NAME} password=$(az account get-access-token --query accessToken -otsv) sslmode=require\" --command \"SELECT case when exists(select * FROM pg_roles where rolname='${NEW_DB_USER_NAME}') then 'exists' else pgaadauth_create_principal_with_oid('${NEW_DB_USER_NAME}', '${NEW_DB_USER_OID}', 'service', false, false) end; GRANT ALL PRIVILEGES on DATABASE ${NEW_DB_NAME} to ${NEW_DB_USER_NAME};\""));
    String dbServerName = "lz3e2e5457e1f4e32b2715862e7cf4b9fc6ca15d71b41fd1c3ae5a49239f0a7";
    String adminDbUserName = "lz6dfc02dcfba562332c";
    String newDbUserName = "dbuser1";
    String newDbUserOid = "f1916966-f7a9-458d-8acc-3c361f6b3fac";
    String newDbName = "newdb7";
    String podName = newDbName + newDbUserName;
    V1Pod pod =
        new V1Pod()
            .metadata(
                new V1ObjectMeta()
                    .name(podName)
                    .labels(Map.of("azure.workload.identity/use", "true")))
            .spec(
                new V1PodSpec()
                    .serviceAccountName(adminDbUserName)
                    .restartPolicy("Never")
                    .addContainersItem(
                        new V1Container()
                            .name("postgres")
                            .image("postgres")
                            .env(
                                List.of(
                                    new V1EnvVar().name("DB_SERVER_NAME").value(dbServerName),
                                    new V1EnvVar()
                                        .name("ADMIN_DB_USER_NAME")
                                        .value(adminDbUserName),
                                    new V1EnvVar().name("NEW_DB_USER_NAME").value(newDbUserName),
                                    new V1EnvVar().name("NEW_DB_USER_OID").value(newDbUserOid),
                                    new V1EnvVar().name("NEW_DB_NAME").value(newDbName)))
                            .command(List.of("sh", "-c", shellCommand))));
    //                              .command(List.of("tail", "-f", "/dev/null"))));

    api.createNamespacedPod("default", pod, null, null, null, null);
    for (int i = 0; i < 300; i++) {
      try {
        Thread.sleep(10000);
        var isDone =
            Optional.ofNullable(api.readNamespacedPod(podName, "default", null).getStatus())
                .map(V1PodStatus::getPhase)
                .map(
                    phase ->
                        switch (phase) {
                          case "Succeeded" -> true;
                          case "Failed" -> throw new RuntimeException("Pod failed");
                          default -> false;
                        })
                .orElse(false);
        if (isDone) {
          break;
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private TokenCredential getManagedAppCredentials() {
    return new ClientSecretCredentialBuilder()
        .clientId(azureConfig.getManagedAppClientId())
        .clientSecret(azureConfig.getManagedAppClientSecret())
        .tenantId(azureConfig.getManagedAppTenantId())
        .build();
  }
}
