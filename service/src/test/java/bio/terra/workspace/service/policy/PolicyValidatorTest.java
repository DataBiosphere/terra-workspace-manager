package bio.terra.workspace.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPolicyInput;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZone;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.ControlledAzureDiskResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class PolicyValidatorTest extends BaseSpringBootUnitTest {
  @Autowired private PolicyValidator policyValidator;

  @MockBean private ResourceDao mockResourceDao;
  @MockBean private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @MockBean private WorkspaceDao mockWorkspaceDao;
  @MockBean private AzureConfiguration mockAzureConfiguration;

  private static final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest("email", "id", Optional.of("token"));
  private static final SpendProfileId SPEND_PROFILE_ID =
      new SpendProfileId(UUID.randomUUID().toString());

  @Test
  void validateWorkspaceConformsToPolicy() {
    // should not throw exception
    policyValidator.validateWorkspaceConformsToPolicy(
        WorkspaceFixtures.buildMcWorkspace(), new TpsPaoGetResult(), userRequest);
  }

  @Test
  void validateWorkspaceConformsToPolicy_reportsErrors() {
    String protectedError = "protected";
    String regionError = "region";
    String groupError = "group";

    PolicyValidator mockPolicyValidator = spy(policyValidator);

    doReturn(List.of(protectedError))
        .when(mockPolicyValidator)
        .validateWorkspaceConformsToProtectedDataPolicy(any(), any(), any());
    doReturn(List.of(regionError))
        .when(mockPolicyValidator)
        .validateWorkspaceConformsToRegionPolicy(any(), any(), any());
    doReturn(List.of(groupError))
        .when(mockPolicyValidator)
        .validateWorkspaceConformsToGroupPolicy(any(), any(), any());

    PolicyConflictException exception =
        assertThrows(
            PolicyConflictException.class,
            () ->
                mockPolicyValidator.validateWorkspaceConformsToPolicy(
                    WorkspaceFixtures.buildMcWorkspace(), new TpsPaoGetResult(), userRequest));

    assertIterableEquals(List.of(regionError, protectedError, groupError), exception.getCauses());
  }

  @Test
  void validateWorkspaceConformsToRegionPolicy_valid() throws Exception {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();
    ControlledAzureStorageContainerResource azureResource =
        ControlledAzureResourceFixtures.getAzureStorageContainer("test");
    ControlledGcsBucketResource gcpResource =
        ControlledGcpResourceFixtures.getBucketResource("test");

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.AZURE, CloudPlatform.GCP));
    when(mockTpsApiDispatch().listValidRegionsForPao(any(), eq(CloudPlatform.GCP)))
        .thenReturn(List.of(gcpResource.getRegion()));
    when(mockResourceDao.listControlledResources(workspace.workspaceId(), CloudPlatform.GCP))
        .thenReturn(List.of(gcpResource));
    when(mockLandingZoneApiDispatch.getLandingZoneRegion(userRequest, workspace))
        .thenReturn(azureResource.getRegion());
    when(mockTpsApiDispatch().listValidRegionsForPao(any(), eq(CloudPlatform.AZURE)))
        .thenReturn(List.of(azureResource.getRegion()));
    when(mockResourceDao.listControlledResources(workspace.workspaceId(), CloudPlatform.AZURE))
        .thenReturn(List.of(azureResource));

    List<String> results =
        policyValidator.validateWorkspaceConformsToRegionPolicy(
            workspace, new TpsPaoGetResult(), userRequest);
    assertTrue(results.isEmpty());
  }

  @Test
  void validateWorkspaceConformsToRegionPolicy_invalidResources() throws Exception {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();
    ControlledAzureStorageContainerResource azureResource =
        ControlledAzureResourceFixtures.getAzureStorageContainer("test");
    ControlledAzureDiskResource azureResourceWrongRegion =
        ControlledAzureResourceFixtures.getAzureDisk("test", "wrongRegion", 1);
    ControlledGcsBucketResource gcpResource =
        ControlledGcpResourceFixtures.getBucketResource("test");

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.AZURE, CloudPlatform.GCP));
    when(mockTpsApiDispatch().listValidRegionsForPao(any(), eq(CloudPlatform.GCP)))
        .thenReturn(List.of(gcpResource.getRegion()));
    when(mockResourceDao.listControlledResources(workspace.workspaceId(), CloudPlatform.GCP))
        .thenReturn(List.of(gcpResource));
    when(mockLandingZoneApiDispatch.getLandingZoneRegion(userRequest, workspace))
        .thenReturn(azureResource.getRegion());
    when(mockTpsApiDispatch().listValidRegionsForPao(any(), eq(CloudPlatform.AZURE)))
        .thenReturn(List.of(azureResource.getRegion()));
    when(mockResourceDao.listControlledResources(workspace.workspaceId(), CloudPlatform.AZURE))
        .thenReturn(List.of(azureResource, azureResourceWrongRegion));

    List<String> results =
        policyValidator.validateWorkspaceConformsToRegionPolicy(
            workspace, new TpsPaoGetResult(), userRequest);
    assertEquals(1, results.size());
  }

  @Test
  void validateWorkspaceConformsToRegionPolicy_invalidLandingZone() throws Exception {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.AZURE));
    when(mockLandingZoneApiDispatch.getLandingZoneRegion(userRequest, workspace))
        .thenReturn("wrongRegion");
    when(mockTpsApiDispatch().listValidRegionsForPao(any(), eq(CloudPlatform.AZURE)))
        .thenReturn(List.of("rightRegion"));

    List<String> results =
        policyValidator.validateWorkspaceConformsToRegionPolicy(
            workspace, new TpsPaoGetResult(), userRequest);
    assertEquals(1, results.size());
  }

  @Test
  void validateWorkspaceConformsToProtectedDataPolicy_valid() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();

    TpsPaoGetResult protectedDataPolicy =
        createPao(
            new TpsPolicyInput()
                .namespace(TpsUtilities.TERRA_NAMESPACE)
                .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.AZURE));
    String protectedLzDef = "protected";
    when(mockLandingZoneApiDispatch.getLandingZone(userRequest, workspace))
        .thenReturn(new ApiAzureLandingZone().definition(protectedLzDef));
    when(mockAzureConfiguration.getProtectedDataLandingZoneDefs())
        .thenReturn(List.of(protectedLzDef));

    List<String> results =
        policyValidator.validateWorkspaceConformsToProtectedDataPolicy(
            workspace, protectedDataPolicy, userRequest);
    assertTrue(results.isEmpty());
  }

  @Test
  void validateWorkspaceConformsToProtectedDataPolicy_invalid() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();

    TpsPaoGetResult protectedDataPolicy =
        createPao(
            new TpsPolicyInput()
                .namespace(TpsUtilities.TERRA_NAMESPACE)
                .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.AZURE));
    when(mockLandingZoneApiDispatch.getLandingZone(userRequest, workspace))
        .thenReturn(new ApiAzureLandingZone().definition("not protected"));
    when(mockAzureConfiguration.getProtectedDataLandingZoneDefs()).thenReturn(List.of("protected"));

    List<String> results =
        policyValidator.validateWorkspaceConformsToProtectedDataPolicy(
            workspace, protectedDataPolicy, userRequest);
    assertEquals(1, results.size());
  }

  @Test
  void validateWorkspaceConformsToProtectedDataPolicy_notAzure() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();

    TpsPaoGetResult protectedDataPolicy =
        createPao(
            new TpsPolicyInput()
                .namespace(TpsUtilities.TERRA_NAMESPACE)
                .name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));

    when(mockWorkspaceDao.listCloudPlatforms(workspace.workspaceId()))
        .thenReturn(List.of(CloudPlatform.GCP));

    List<String> results =
        policyValidator.validateWorkspaceConformsToProtectedDataPolicy(
            workspace, protectedDataPolicy, userRequest);
    assertEquals(1, results.size());
  }

  @Test
  void validateWorkspaceConformsToProtectedDataPolicy_noPolicy() {
    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(UUID.randomUUID())
            .spendProfileId(SPEND_PROFILE_ID)
            .build();

    TpsPaoGetResult protectedDataPolicy =
        createPao(
            new TpsPolicyInput().namespace("other").name(TpsUtilities.PROTECTED_DATA_POLICY_NAME));

    List<String> results =
        policyValidator.validateWorkspaceConformsToProtectedDataPolicy(
            workspace, protectedDataPolicy, userRequest);
    assertTrue(results.isEmpty());
  }

  private static TpsPaoGetResult createPao(TpsPolicyInput... inputs) {
    TpsPolicyInputs tpsPolicyInputs = new TpsPolicyInputs().inputs(Arrays.stream(inputs).toList());
    return new TpsPaoGetResult()
        .component(TpsComponent.WSM)
        .objectType(TpsObjectType.WORKSPACE)
        .objectId(UUID.randomUUID())
        .sourcesObjectIds(Collections.emptyList())
        .attributes(tpsPolicyInputs)
        .effectiveAttributes(tpsPolicyInputs);
  }
}
