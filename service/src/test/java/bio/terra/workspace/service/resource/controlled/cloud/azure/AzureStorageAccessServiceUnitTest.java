package bio.terra.workspace.service.resource.controlled.cloud.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.StorageAccountKeyProvider;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.mock.mockito.MockBean;

public class AzureStorageAccessServiceUnitTest extends BaseUnitTest {

  private final OffsetDateTime startTime = OffsetDateTime.now().minusMinutes(15);
  private final OffsetDateTime expiryTime = OffsetDateTime.now().plusMinutes(60);
  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest("foo@example.com", "sub", Optional.of("token"));

  @MockBean private SamService samService;
  @MockBean private FeatureConfiguration featureConfig;
  private AzureStorageAccessService azureStorageAccessService;

  @BeforeEach
  public void setup() {
    var keyProvider = mock(StorageAccountKeyProvider.class);
    var cred = new StorageSharedKeyCredential("fake", "fake");
    when(keyProvider.getStorageAccountKey(any(), any())).thenReturn(cred);
    azureStorageAccessService =
        new AzureStorageAccessService(samService, keyProvider, featureConfig);
  }

  private ControlledAzureStorageResource buildStorageAccount() {
    return new ControlledAzureStorageResource(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "fake",
        "description",
        CloningInstructions.COPY_NOTHING,
        null,
        PrivateResourceState.NOT_APPLICABLE,
        AccessScopeType.ACCESS_SCOPE_SHARED,
        ManagedByType.MANAGED_BY_USER,
        null,
        "fake",
        "us-east1",
        /*resourceLineage=*/ null,
        /*properties*/ Map.of());
  }

  private ControlledAzureStorageContainerResource buildStorageContainerResource(
      PrivateResourceState privateResourceState,
      AccessScopeType accessScopeType,
      ManagedByType managedByType) {
    return new ControlledAzureStorageContainerResource(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "fake",
        "description",
        CloningInstructions.COPY_NOTHING,
        null,
        privateResourceState,
        accessScopeType,
        managedByType,
        null,
        UUID.randomUUID(),
        "fake",
        /*resourceLineage=*/ null,
        /*properties*/ Map.of());
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
            ManagedByType.MANAGED_BY_USER);

    var ipRange = "168.1.5.60-168.1.5.70";
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageAccountResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            ipRange,
            null,
            null);

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
            ManagedByType.MANAGED_BY_USER);

    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            null,
            null,
            null);

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), false);
    verify(samService)
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
            ManagedByType.MANAGED_BY_USER);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of());

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                UUID.randomUUID(),
                storageContainerResource,
                storageAccountResource,
                startTime,
                expiryTime,
                userRequest,
                null,
                null,
                null));

    verify(samService)
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
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            null,
            null,
            null);

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), false);

    verify(samService)
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
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            null,
            "testing/blob-path",
            null);

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), true);
  }

  @Test
  void createAzureStorageContainerSasToken_permissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.WRITE_ACTION,
                SamConstants.SamControlledResourceActions.READ_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            null,
            "testing/blob-path",
            "ld");

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("dl"), true);
  }

  @Test
  void createAzureStorageContainerSasToken_forbiddenPermissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(List.of(SamConstants.SamControlledResourceActions.READ_ACTION));

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                UUID.randomUUID(),
                storageContainerResource,
                storageAccountResource,
                startTime,
                expiryTime,
                userRequest,
                null,
                null,
                "rwdl"),
        "Asking for delete + write when we should only have READ_ACTION should result in an exception ");
  }

  @Test
  void createAzureStorageContainerSasToken_invalidPermissions() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));

    assertThrows(
        ForbiddenException.class,
        () ->
            azureStorageAccessService.createAzureStorageContainerSasToken(
                UUID.randomUUID(),
                storageContainerResource,
                storageAccountResource,
                startTime,
                expiryTime,
                userRequest,
                null,
                null,
                "!@#"),
        "Nonsense characters should result in an exception ");
  }

  @Test
  void createAzureStorageContainerSasToken_blobName() throws InterruptedException {
    var storageAccountResource = buildStorageAccount();
    var storageContainerResource =
        buildStorageContainerResource(
            PrivateResourceState.ACTIVE,
            AccessScopeType.ACCESS_SCOPE_PRIVATE,
            ManagedByType.MANAGED_BY_APPLICATION);
    when(samService.listResourceActions(
            ArgumentMatchers.eq(userRequest),
            ArgumentMatchers.eq(storageContainerResource.getCategory().getSamResourceName()),
            ArgumentMatchers.eq(storageContainerResource.getResourceId().toString())))
        .thenReturn(
            List.of(
                SamConstants.SamControlledResourceActions.READ_ACTION,
                SamConstants.SamControlledResourceActions.WRITE_ACTION));

    var result =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            UUID.randomUUID(),
            storageContainerResource,
            storageAccountResource,
            startTime,
            expiryTime,
            userRequest,
            null,
            "foo/the/bar.baz",
            null);

    assertValidToken(result.sasToken(), BlobContainerSasPermission.parse("racwdl"), true);
  }
}
