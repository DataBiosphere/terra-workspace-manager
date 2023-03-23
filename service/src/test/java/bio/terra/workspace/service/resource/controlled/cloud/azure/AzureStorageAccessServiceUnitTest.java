package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.Endpoints;
import com.azure.resourcemanager.storage.models.PublicEndpoints;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.mock.mockito.MockBean;

public class AzureStorageAccessServiceUnitTest extends BaseAzureUnitTest {
  private final UUID landingZoneId = UUID.randomUUID();
  private final OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
  private final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(60);
  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest("foo@example.com", "sub", Optional.of("token"));
  private final ApiAzureLandingZoneDeployedResource sharedStorageAccount =
      new ApiAzureLandingZoneDeployedResource().resourceId(UUID.randomUUID().toString());

  @MockBean private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @MockBean private AzureCloudContextService mockAzureCloudContextService;
  @MockBean private AzureConfiguration mockAzureConfiguration;

  private AzureStorageAccessService azureStorageAccessService;

  @BeforeEach
  public void setup() throws InterruptedException {
    var keyProvider = mock(StorageAccountKeyProvider.class);
    var cred = new StorageSharedKeyCredential("fake", "fake");
    when(keyProvider.getStorageAccountKey(any(), any())).thenReturn(cred);
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(eq(userRequest)))
        .thenReturn(userRequest.getEmail());
    when(mockSamService().getWsmServiceAccountToken()).thenReturn("wsm-token");
    azureStorageAccessService =
        new AzureStorageAccessService(
            mockSamService(),
            mockCrlService(),
            keyProvider,
            mockControlledResourceMetadataManager(),
            mockLandingZoneApiDispatch,
            mockAzureCloudContextService,
            mockFeatureConfiguration(),
            mockAzureConfiguration);
  }

  private ControlledAzureStorageResource buildStorageAccount() {
    return ControlledAzureStorageResource.builder()
        .common(ControlledResourceFixtures.makeDefaultControlledResourceFields(UUID.randomUUID()))
        .storageAccountName("fake")
        .build();
  }

  private ControlledAzureStorageContainerResource buildStorageContainerResource(
      PrivateResourceState privateResourceState,
      AccessScopeType accessScopeType,
      ManagedByType managedByType,
      UUID storageAccountId) {
    return ControlledResourceFixtures.makeDefaultAzureStorageContainerResourceBuilder(
            /*workspaceId=*/ UUID.randomUUID())
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .managedBy(managedByType)
                .accessScope(accessScopeType)
                .privateResourceState(privateResourceState)
                .build())
        .storageAccountId(storageAccountId)
        .build();
  }

  private void assertValidToken(
      String sas, BlobContainerSasPermission expectedPermissions, boolean blobToken) {
    Pattern protocolRegex = Pattern.compile("spr=https&");
    // SAS tokens start and expiry times are UTC
    Pattern startTimeRegex =
        Pattern.compile(
            "st="
                + startTime
                    .atZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE));
    Pattern expiryTimeRegex =
        Pattern.compile(
            "se="
                + expiryTime
                    .atZoneSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE));
    Pattern signedContainerResourceRegex = Pattern.compile("sr=c&");
    Pattern signedBlobResourceRegex = Pattern.compile("sr=b&");
    Pattern permissionsRegex = Pattern.compile("sp=" + expectedPermissions.toString() + "&");

    assertThat("SAS is https", protocolRegex.matcher(sas).find());
    assertThat("SAS validity starts today", startTimeRegex.matcher(sas).find());
    assertThat("SAS validity ends today", expiryTimeRegex.matcher(sas).find());
    if (blobToken) {
      assertThat("SAS is for a blob resource", signedBlobResourceRegex.matcher(sas).find());
    } else {
      assertThat(
          "SAS is for a container resource", signedContainerResourceRegex.matcher(sas).find());
    }
    assertThat("SAS grants correct permissions", permissionsRegex.matcher(sas).find());
  }

  @Test
  public void createAzureStorageContainerSasToken_readonly() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            storageAccountResource.getResourceId());

    var ipRange = "168.1.5.60-168.1.5.70";
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageAccountResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(ipRange, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("rl"), false);
    assertTrue(
        result.sasToken().contains("sip=" + ipRange),
        "the SignedIP was added to the query parameters");
  }

  @Test
  public void createAzureStorageContainerSasToken_readwrite() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            storageAccountResource.getResourceId());

    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), false);
    verify(mockSamService())
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_USER_SHARED),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  @Test
  public void createAzureStorageContainerSasToken_notAuthorized() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of());
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                storageContainerResource.getWorkspaceId(),
                storageContainerResource.getResourceId(),
                userRequest,
                new SasTokenOptions(null, null, null, null, null)));

    verify(mockSamService())
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_USER_SHARED),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  @Test
  public void createAzureStorageContainerSasToken_privateAccessContainer()
      throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), false);

    verify(mockSamService())
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_APPLICATION_PRIVATE),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  @Test
  void createAzureStorageContainerSasToken_blobPath() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, "testing/blob-path", null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), true);
  }

  @Test
  void createAzureStorageContainerSasToken_permissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, "testing/blob-path", "ld"));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("dl"), true);
  }

  @Test
  void createAzureStorageContainerSasToken_forbiddenPermissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                storageContainerResource.getWorkspaceId(),
                storageContainerResource.getResourceId(),
                userRequest,
                new SasTokenOptions(null, null, null, null, "rwdl")),
        "Asking for delete + write when we should only have READ_ACTION should result in an exception ");
  }

  @Test
  void createAzureStorageContainerSasToken_invalidPermissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                storageContainerResource.getWorkspaceId(),
                storageContainerResource.getResourceId(),
                userRequest,
                new SasTokenOptions(null, null, null, null, "!@#")),
        "Nonsense characters should result in an exception ");
  }

  @Test
  void createAzureStorageContainerSasToken_blobName() throws InterruptedException {
    var blobName = "foo/the/bar.baz";
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION,
            storageAccountResource.getResourceId());
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageAccountResource, storageContainerResource, Optional.empty());

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, blobName, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), true);
    assertTrue(
        result
            .sasUrl()
            .contains(storageContainerResource.getStorageContainerName() + "/" + blobName + "?"));
  }

  @Test
  public void createAzureStorageContainerSasToken_basedOnLzSharedStorageAccount_Success()
      throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            null);

    var ipRange = "168.1.5.60-168.1.5.70";
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(null, storageContainerResource, Optional.of(true));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(ipRange, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), false);
    assertTrue(
        result.sasToken().contains("sip=" + ipRange),
        "the SignedIP was added to the query parameters");
  }

  @Test
  public void createAzureStorageContainerSasToken_LzSharedStorageAccountNotFound_Failure()
      throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER,
            null);

    var ipRange = "168.1.5.60-168.1.5.70";
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(null, storageContainerResource, Optional.of(false));

    assertThrows(
        IllegalStateException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                storageContainerResource.getWorkspaceId(),
                storageContainerResource.getResourceId(),
                userRequest,
                new SasTokenOptions(ipRange, startTime, expiryTime, null, null)));
  }

  /** Set up mocks behavior based on testing scenario */
  private void setupMocks(
      ControlledAzureStorageResource storageAccountResource,
      ControlledAzureStorageContainerResource storageContainerResource,
      Optional<Boolean> lzSharedStorageAccountExists) {
    when(mockControlledResourceMetadataManager()
            .validateControlledResourceAndAction(
                eq(userRequest),
                eq(storageContainerResource.getWorkspaceId()),
                eq(storageContainerResource.getResourceId()),
                eq(SamConstants.SamControlledResourceActions.READ_ACTION)))
        .thenReturn(storageContainerResource);
    if (storageContainerResource.getStorageAccountId() != null) {
      when(mockControlledResourceMetadataManager()
              .validateControlledResourceAndAction(
                  eq(userRequest),
                  eq(storageContainerResource.getWorkspaceId()),
                  eq(storageContainerResource.getStorageAccountId()),
                  eq(SamConstants.SamControlledResourceActions.READ_ACTION)))
          .thenReturn(storageAccountResource);
    } else {
      when(mockLandingZoneApiDispatch.getLandingZoneId(
              any(), eq(storageContainerResource.getWorkspaceId())))
          .thenReturn(landingZoneId);
      Optional<ApiAzureLandingZoneDeployedResource> lzSharedStorageAccount =
          lzSharedStorageAccountExists.isPresent() && lzSharedStorageAccountExists.get()
              ? Optional.of(sharedStorageAccount)
              : Optional.empty();
      when(mockLandingZoneApiDispatch.getSharedStorageAccount(any(), eq(landingZoneId)))
          .thenReturn(lzSharedStorageAccount);
      StorageManager mockStorageManager = mock(StorageManager.class);
      StorageAccount mockStorageAccount = mock(StorageAccount.class);
      StorageAccounts mockStorageAccounts = mock(StorageAccounts.class);
      PublicEndpoints mockPublicEndpoints = mock(PublicEndpoints.class);
      Endpoints mockEndpoints = mock(Endpoints.class);
      when(mockStorageManager.storageAccounts()).thenReturn(mockStorageAccounts);
      when(mockCrlService().getStorageManager(any(), any())).thenReturn(mockStorageManager);
      when(mockEndpoints.blob())
          .thenReturn(String.format("https://%s.blob.core.windows.net", "mockStorageAccountName"));
      when(mockPublicEndpoints.primary()).thenReturn(mockEndpoints);
      when(mockStorageAccount.name()).thenReturn("mockStorageAccountName");
      when(mockStorageAccount.endPoints()).thenReturn(mockPublicEndpoints);
      when(mockStorageAccounts.getById(any())).thenReturn(mockStorageAccount);
    }
  }
}
