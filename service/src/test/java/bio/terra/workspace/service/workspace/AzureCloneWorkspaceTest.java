package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures.DEFAULT_AZURE_RESOURCE_REGION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.iam.BearerToken;
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
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

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
    var azureProfile = new AzureProfile(
        "0cb7a640-45a2-4ed6-be9f-63519f86e04b",
        "ffd1069e-e34f-4d87-a8b8-44abfcba39af",
        AzureEnvironment.AZURE);
    var containerServiceManager = ContainerServiceManager.authenticate(getManagedAppCredentials(), azureProfile);

//    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
//    UUID landingZoneId = UUID.fromString("75f4d9ce-7aaf-496a-b39e-aa420a6ac26c");
//    var clusterResource = landingZoneApiDispatch.getSharedKubernetesCluster(bearerToken, landingZoneId).orElseThrow(() -> new RuntimeException("No shared cluster found"));
    var rawKubeConfig = containerServiceManager.kubernetesClusters().manager().serviceClient().getManagedClusters().listClusterUserCredentials("mrg-terra-dev-previ-20230330095258", "lz0edbd383f52a493c017bcc6").kubeconfigs().stream().findFirst().orElseThrow(() -> new RuntimeException("No kubeconfig found"));
    var kubeConfig = KubeConfig.loadKubeConfig(new InputStreamReader(new ByteArrayInputStream(rawKubeConfig.value())));
    var userToken = kubeConfig.getCredentials().get("token");
    ApiClient client = Config.fromToken(kubeConfig.getServer(), userToken).setSslCaCert(new ByteArrayInputStream(
        Base64.getDecoder().decode(kubeConfig.getCertificateAuthorityData().getBytes())));
    CoreV1Api api = new CoreV1Api(client);
    V1Pod pod =
        new V1Pod()
            .metadata(new V1ObjectMeta().name("apod"))
            .spec(new V1PodSpec().addContainersItem(new V1Container().name("www").image("nginx")));

    api.createNamespacedPod("default", pod, null, null, null, null);

  }
  private TokenCredential getManagedAppCredentials() {
    return new ClientSecretCredentialBuilder()
        .clientId(azureConfig.getManagedAppClientId())
        .clientSecret(azureConfig.getManagedAppClientSecret())
        .tenantId(azureConfig.getManagedAppTenantId())
        .build();
  }
}
