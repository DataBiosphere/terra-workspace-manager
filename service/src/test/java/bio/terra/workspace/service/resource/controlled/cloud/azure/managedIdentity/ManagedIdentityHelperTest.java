package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.azure.resourcemanager.msi.models.Identity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("azure-unit")
class ManagedIdentityHelperTest extends BaseMockitoStrictStubbingTest {

  @Mock private ResourceDao mockResourceDao;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfiguration;

  @Test
  void getManagedIdentity_HappyPath() {
    // this test is just putting all the pieces together
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(workspaceId, identityResource.getName()))
        .thenReturn(identityResource);

    var mockIdentity = mock(Identity.class);
    var mockMsiManager = mock(MsiManager.class);
    var mockIdentities = mock(Identities.class);
    var mockAzureCloudContext = mock(AzureCloudContext.class);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            identityResource.getManagedIdentityName()))
        .thenReturn(mockIdentity);
    when(mockCrlService.getMsiManager(mockAzureCloudContext, mockAzureConfiguration))
        .thenReturn(mockMsiManager);

    var managedIdentityHelper =
        new ManagedIdentityHelper(mockResourceDao, mockCrlService, mockAzureConfiguration);

    var identity =
        managedIdentityHelper.getManagedIdentity(
            mockAzureCloudContext, workspaceId, identityResource.getName());
    assertThat(identity, equalTo(Optional.of(mockIdentity)));
  }

  @Test
  void getManagedIdentityResource_successWithName() {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(workspaceId, identityResource.getName()))
        .thenReturn(identityResource);
    var managedIdentityHelper =
        new ManagedIdentityHelper(mockResourceDao, mockCrlService, mockAzureConfiguration);

    var maybeIdentityResource =
        managedIdentityHelper.getManagedIdentityResource(workspaceId, identityResource.getName());

    assertThat(maybeIdentityResource.isPresent(), equalTo(true));
    assertThat(maybeIdentityResource.get(), equalTo(identityResource));
  }

  @Test
  void getManagedIdentityResource_successOnFallback() {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(
            workspaceId, identityResource.getResourceId().toString()))
        .thenThrow(new ResourceNotFoundException("not found"));
    when(mockResourceDao.getResource(workspaceId, identityResource.getResourceId()))
        .thenReturn(identityResource);

    var managedIdentityHelper =
        new ManagedIdentityHelper(mockResourceDao, mockCrlService, mockAzureConfiguration);

    var maybeIdentityResource =
        managedIdentityHelper.getManagedIdentityResource(
            workspaceId, identityResource.getResourceId().toString());

    assertThat(maybeIdentityResource.isPresent(), equalTo(true));
    assertThat(maybeIdentityResource.get(), equalTo(identityResource));
  }

  @Test
  void getManagedIdentityResource_notFound() {
    var workspaceId = UUID.randomUUID();
    var creationParameters =
        ControlledAzureResourceFixtures.getAzureManagedIdentityCreationParameters();
    var identityResource =
        ControlledAzureResourceFixtures.makeDefaultControlledAzureManagedIdentityResourceBuilder(
                creationParameters, workspaceId)
            .build();
    when(mockResourceDao.getResourceByName(
            workspaceId, identityResource.getResourceId().toString()))
        .thenThrow(new ResourceNotFoundException("not found"));
    when(mockResourceDao.getResource(workspaceId, identityResource.getResourceId()))
        .thenThrow(new ResourceNotFoundException("not found"));

    var managedIdentityHelper =
        new ManagedIdentityHelper(mockResourceDao, mockCrlService, mockAzureConfiguration);

    var maybeIdentityResource =
        managedIdentityHelper.getManagedIdentityResource(
            workspaceId, identityResource.getResourceId().toString());

    assertThat(maybeIdentityResource.isEmpty(), equalTo(true));
  }

  @Test
  void getIdentity_success() {
    var managedIdentityHelper =
        new ManagedIdentityHelper(mockResourceDao, mockCrlService, mockAzureConfiguration);
    var mockIdentity = mock(Identity.class);
    var mockMsiManager = mock(MsiManager.class);
    var mockIdentities = mock(Identities.class);
    var mockAzureCloudContext = mock(AzureCloudContext.class);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(), "fake"))
        .thenReturn(mockIdentity);
    when(mockCrlService.getMsiManager(mockAzureCloudContext, mockAzureConfiguration))
        .thenReturn(mockMsiManager);
    when(mockMsiManager.identities()).thenReturn(mockIdentities);
    when(mockIdentities.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(), "fake"))
        .thenReturn(mockIdentity);

    var identity = managedIdentityHelper.getIdentity(mockAzureCloudContext, "fake");

    assertThat(identity, equalTo(mockIdentity));
  }
}
