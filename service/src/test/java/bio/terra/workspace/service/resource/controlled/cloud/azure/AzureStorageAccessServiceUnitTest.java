package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.atMost;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureSpringBootUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import com.azure.core.management.AzureEnvironment;
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

public class AzureStorageAccessServiceUnitTest extends BaseAzureSpringBootUnitTest {
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
  public void setup() {
    var keyProvider = mock(StorageAccountKeyProvider.class);
    var cred = new StorageSharedKeyCredential("fake", "fake");
    when(keyProvider.getStorageAccountKey(any(), any())).thenReturn(cred);
    when(mockSamService().getSamUser(userRequest))
        .thenReturn(new SamUser("example@example.com", "123ABC", new BearerToken("token")));
    when(mockSamService().getWsmServiceAccountToken()).thenReturn("wsm-token");
    when(mockAzureConfiguration.getAzureEnvironment()).thenReturn(AzureEnvironment.AZURE);
    azureStorageAccessService =
        new AzureStorageAccessService(
            mockSamService(),
            mockCrlService(),
            keyProvider,
            mockControlledResourceMetadataManager(),
            mockLandingZoneApiDispatch,
            mockAzureCloudContextService,
            mockFeatureConfiguration(),
            mockAzureConfiguration,
            mockWorkspaceService());
  }

  private ControlledAzureStorageContainerResource buildStorageContainerResource(
      PrivateResourceState privateResourceState,
      AccessScopeType accessScopeType,
      ManagedByType managedByType) {
    return ControlledAzureResourceFixtures.makeDefaultAzureStorageContainerResourceBuilder(
            /* workspaceId= */ UUID.randomUUID())
        .common(
            ControlledResourceFixtures.makeDefaultControlledResourceFieldsBuilder()
                .managedBy(managedByType)
                .accessScope(accessScopeType)
                .privateResourceState(privateResourceState)
                .build())
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
    Pattern contentDispositionRegex = Pattern.compile("rscd=" + "123ABC");

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
    assertThat(
        "SAS contains user subject ID in content disposition query parameter",
        contentDispositionRegex.matcher(sas).find());
  }

  @Test
  public void createAzureStorageContainerSasToken_readonly() throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);

    var ipRange = "168.1.5.60-168.1.5.70";
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

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
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);

    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdlt"), false);
    verify(mockSamService())
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_USER_SHARED),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  @Test
  public void createAzureStorageContainerSasToken_notAuthorized() throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of());
    setupMocks(storageContainerResource, true);

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
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdlt"), false);

    verify(mockSamService())
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_APPLICATION_PRIVATE),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  @Test
  void createAzureStorageContainerSasToken_blobPath() throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, "testing/blob-path", null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdlt"), true);
  }

  @Test
  void createAzureStorageContainerSasToken_permissions() throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

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
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);

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
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageContainerResource, true);

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
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, blobName, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdlt"), true);
    assertTrue(
        result
            .sasUrl()
            .contains(storageContainerResource.getStorageContainerName() + "/" + blobName + "?"));
  }

  @Test
  void createAzureStorageContainerSasUrl_AzureCommercial() throws InterruptedException {

    var blobName = "foo/the/bar.baz";

    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, blobName, null));

    assertTrue(result.sasUrl().contains("core.windows.net"));
  }

  @Test
  void createAzureStorageContainerSasUrl_AzureGovernment() throws InterruptedException {
    var blobName = "foo/the/bar.baz";

    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockAzureConfiguration.getAzureEnvironment())
        .thenReturn(AzureEnvironment.AZURE_US_GOVERNMENT);
//    when(mockCrlService().getAzureEnvironmentFromName(any()))
//        .thenReturn(AzureEnvironment.AZURE_US_GOVERNMENT);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(null, startTime, expiryTime, blobName, null));

    assertTrue(result.sasUrl().contains("core.usgovcloudapi.net"));
  }

  @Test
  public void createAzureStorageContainerSasToken_basedOnLzSharedStorageAccount_Success()
      throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);

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
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(ipRange, startTime, expiryTime, null, null));

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdlt"), false);
    assertTrue(
        result.sasToken().contains("sip=" + ipRange),
        "the SignedIP was added to the query parameters");
  }

  @Test
  void createAzureStorageContainerSasToken_computesAuthHash() throws InterruptedException {
    // build a custom container here instead of using the fixture method
    // since randomly generated identifiers make the hash unstable for
    // our assertions in this test
    var storageContainerResource =
        ControlledAzureStorageContainerResource.builder()
            .common(
                ControlledResourceFields.builder()
                    .workspaceUuid(UUID.fromString("68f65410-cb18-4b5a-8518-0a5ef8065f72"))
                    .resourceId(UUID.fromString("2d08d1a4-882d-4483-8c2c-c70b68b10be6"))
                    .name("name")
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .createdByEmail("example@example.com")
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .build())
            .storageContainerName("name")
            .build();

    when(mockSamService()
            .listResourceActions(
                userRequest,
                storageContainerResource.getCategory().getSamResourceName(),
                storageContainerResource.getResourceId().toString()))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            storageContainerResource.getWorkspaceId(),
            storageContainerResource.getResourceId(),
            userRequest,
            new SasTokenOptions(
                null,
                OffsetDateTime.parse(
                    "2023-05-03T09:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                OffsetDateTime.parse(
                    "2023-05-03T10:15:30-05:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                null,
                null));

    assertEquals(
        "19ABE52309A3733175CCBAD669CD895EED970910E1E8784A4CA09624E723220B", result.sha256());
  }

  @Test
  public void createAzureStorageContainerSasToken_LzSharedStorageAccountNotFound_Failure()
      throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);

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
    setupMocks(storageContainerResource, false);

    assertThrows(
        IllegalStateException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                storageContainerResource.getWorkspaceId(),
                storageContainerResource.getResourceId(),
                userRequest,
                new SasTokenOptions(ipRange, startTime, expiryTime, null, null)));
  }

  @Test
  public void createAzureStorageContainerSasToken_cachesSamResults() throws InterruptedException {
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.NOT_APPLICABLE,
            AccessScopeType.ACCESS_SCOPE_SHARED,
            ManagedByType.MANAGED_BY_USER);

    when(mockSamService()
            .listResourceActions(
                ArgumentMatchers.eq(userRequest),
                ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
                ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));
    setupMocks(storageContainerResource, true);
    when(mockCrlService().getAzureEnvironmentFromName(any())).thenReturn(AzureEnvironment.AZURE);

    azureStorageAccessService.createAzureStorageContainerSasToken(
        storageContainerResource.getWorkspaceId(),
        storageContainerResource.getResourceId(),
        userRequest,
        new SasTokenOptions(null, startTime, expiryTime, null, null));
    azureStorageAccessService.createAzureStorageContainerSasToken(
        storageContainerResource.getWorkspaceId(),
        storageContainerResource.getResourceId(),
        userRequest,
        new SasTokenOptions(null, startTime, expiryTime, null, null));

    verify(mockSamService(), atMost(1)).getSamUser(userRequest);
    verify(mockControlledResourceMetadataManager(), atMost(1))
        .validateControlledResourceAndAction(
            eq(userRequest),
            eq(storageContainerResource.getWorkspaceId()),
            eq(storageContainerResource.getResourceId()),
            eq(SamConstants.SamControlledResourceActions.READ_ACTION));
    verify(mockSamService(), atMost(1))
        .listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(SamConstants.SamResource.CONTROLLED_USER_SHARED),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString()));
  }

  /** Set up mocks behavior based on testing scenario */
  private void setupMocks(
      ControlledAzureStorageContainerResource storageContainerResource,
      boolean lzSharedStorageAccountExists) {
    when(mockControlledResourceMetadataManager()
            .validateControlledResourceAndAction(
                eq(userRequest),
                eq(storageContainerResource.getWorkspaceId()),
                eq(storageContainerResource.getResourceId()),
                eq(SamConstants.SamControlledResourceActions.READ_ACTION)))
        .thenReturn(storageContainerResource);
    when(mockWorkspaceService().getWorkspace(storageContainerResource.getWorkspaceId()))
        .thenReturn(WorkspaceFixtures.buildMcWorkspace(storageContainerResource.getWorkspaceId()));
    when(mockLandingZoneApiDispatch.getLandingZoneId(
            any(),
            argThat(a -> a.getWorkspaceId().equals(storageContainerResource.getWorkspaceId()))))
        .thenReturn(landingZoneId);
    Optional<ApiAzureLandingZoneDeployedResource> lzSharedStorageAccount =
        lzSharedStorageAccountExists ? Optional.of(sharedStorageAccount) : Optional.empty();
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
