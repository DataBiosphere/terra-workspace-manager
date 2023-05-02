package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.utils.MockMvcUtils.DEFAULT_USER_EMAIL;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.service.workspace.model.AwsCloudContext.AwsCloudContextV1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.aws.resource.discovery.Environment;
import bio.terra.workspace.app.configuration.external.AwsConfiguration.Authentication;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledAwsResourceFixtures;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder.ControlledAwsS3StorageFolderResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.exceptions.InvalidSerializedVersionException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

@Tag("aws-unit")
@TestInstance(Lifecycle.PER_CLASS)
public class AwsCloudContextUnitTest extends BaseUnitTest {
  private static final long v1Version = AwsCloudContextV1.getVersion();
  private static final String MAJOR_VERSION = "v9";
  private static final String ORGANIZATION_ID = "o-organization-id";
  private static final String ACCOUNT_ID = "012345678910";
  private static final String TENANT_ALIAS = "terra-tenant";
  private static final String ENVIRONMENT_ALIAS = "terra-environment";

  private static final Arn WSM_ROLE_ARN =
      Arn.fromString("arn:aws:iam::111111111111:role/develwest-TerraNWorkspaceManager");

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private ResourceDao resourceDao;

  // TODO-Dex move this to utils
  @MockBean private AwsCloudContextService awsCloudContextService;

  @BeforeAll
  public void setup() {
    Mockito.when(mockFeatureService().awsEnabled()).thenReturn(true);
  }

  @Test
  public void serdesTest() {
    String v1Json =
        String.format(
            "{\"version\": %d, \"majorVersion\": \"%s\", \"organizationId\": \"%s\", \"accountId\": \"%s\", \"tenantAlias\": \"%s\", \"environmentAlias\": \"%s\" }",
            v1Version, MAJOR_VERSION, ORGANIZATION_ID, ACCOUNT_ID, TENANT_ALIAS, ENVIRONMENT_ALIAS);

    // Case 1: successful V1 deserialization
    AwsCloudContext goodV1 = AwsCloudContext.deserialize(v1Json);
    assertNotNull(goodV1);
    assertEquals(goodV1.getMajorVersion(), MAJOR_VERSION);
    assertEquals(goodV1.getOrganizationId(), ORGANIZATION_ID);
    assertEquals(goodV1.getAccountId(), ACCOUNT_ID);
    assertEquals(goodV1.getTenantAlias(), TENANT_ALIAS);
    assertEquals(goodV1.getEnvironmentAlias(), ENVIRONMENT_ALIAS);

    // Case 2: bad V1 format
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize("{\"version\": 0}"),
        "Bad V1 JSON should throw");

    // Case 3: junk input
    assertThrows(
        InvalidSerializedVersionException.class,
        () -> AwsCloudContext.deserialize("{\"foo\": 15, \"bar\": \"xyz\"}"),
        "Junk JSON should throw");
  }

  @Test
  public void deleteAwsContext_deletesControlledResourcesInDb() throws Exception {
    UUID workspaceUuid = UUID.randomUUID();
    var workspace =
        new Workspace(
            workspaceUuid,
            "my-user-facing-id",
            "deleteAwsContextDeletesControlledResources",
            "description",
            new SpendProfileId("spend-profile"),
            Collections.emptyMap(),
            WorkspaceStage.MC_WORKSPACE,
            DEFAULT_USER_EMAIL,
            null);
    workspaceDao.createWorkspace(workspace, /* applicationIds= */ null);

    // Create a cloud context record in the DB
    AwsCloudContext fakeContext =
        new AwsCloudContext(
            "fakeMajorVersion",
            "fakeOrganizationId",
            "fakeAccountId",
            "fakeTenantAlias",
            "fakeEnvironmentAlias");
    final String flightId = UUID.randomUUID().toString();
    workspaceDao.createCloudContextStart(workspaceUuid, CloudPlatform.AWS, flightId);
    workspaceDao.createCloudContextFinish(
        workspaceUuid, CloudPlatform.AWS, fakeContext.serialize(), flightId);

    // Create a controlled resource in the DB
    // TODO(TERRA-470) create S3 folder
    ControlledAwsS3StorageFolderResource s3Folder =
        ControlledAwsResourceFixtures.makeDefaultControlledAwsS3StorageFolder(workspaceUuid);
    ControlledResourceFixtures.insertControlledResourceRow(resourceDao, s3Folder);

    // Also create a reference pointing to the same "cloud" resource
    // TODO(TERRA-195) Create a referenced S3 bucket

    setupCrlMocks();

    try (MockedStatic<AwsUtils> awsUtilsMockedStatic = Mockito.mockStatic(AwsUtils.class)) {

     awsUtilsMockedStatic
          .when(() -> AwsUtils.deleteFolder(any(), any(), any(), any()))
         .thenAnswer((Answer<Void>) invocation -> null);

      workspaceService.deleteAwsCloudContext(workspace, USER_REQUEST);
    }

    // try (MockedStatic<AwsUtils> mockAwsUtils = Mockito.mockStatic(AwsUtils.class)) {

    // }
    // Delete the AWS context through the service

    // Verify the context and resource have both been deleted from the DB
    // TODO-Dex
    assertTrue(workspaceDao.getCloudContext(workspaceUuid, CloudPlatform.AWS).isEmpty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> resourceDao.getResource(workspaceUuid, s3Folder.getResourceId()));

    // Verify the reference still exists, even though the underlying "cloud" resource was deleted
    // TODO(TERRA-195) Check that reference S3 bucket exists
  }

  private void setupCrlMocks() {
    // TODO(TERRA-499) set up CRL mocks when CRL us used (mock S3 resource deletion)
    Environment mockEnvironment = Mockito.mock(Environment.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(mockEnvironment.getWorkspaceManagerRoleArn()).thenReturn(WSM_ROLE_ARN);

    Authentication mockAuthentication =
        Mockito.mock(Authentication.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(mockAuthentication.getGoogleJwtAudience()).thenReturn("googleJwtAudience");
    Authentication authentication = new Authentication();
    authentication.setGoogleJwtAudience("googleJwtAudience");
    authentication.setCredentialLifetimeSeconds(900);

    Mockito.when(awsCloudContextService.getRequiredAuthentication()).thenReturn(authentication);
    Mockito.when(awsCloudContextService.discoverEnvironment()).thenReturn(mockEnvironment);

     SdkHttpResponse sdkHttpResponse =
         ((SdkHttpResponse.Builder) SdkHttpResponse.builder().statusCode(200)).build();
     ListObjectsV2Response listResponse =
         ((ListObjectsV2Response.Builder)
                 ListObjectsV2Response.builder().sdkHttpResponse(sdkHttpResponse))
             .build();
     DeleteObjectsResponse deleteResponse =
         ((DeleteObjectsResponse.Builder)
                 DeleteObjectsResponse.builder().sdkHttpResponse(sdkHttpResponse))
             .build();

   S3Client mockS3Client = Mockito.mock(S3Client.class, Mockito.RETURNS_DEEP_STUBS);
     Mockito.when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class)))
         .thenReturn(listResponse);
     Mockito.when(mockS3Client.deleteObjects(any(DeleteObjectsRequest.class)))
         .thenReturn(deleteResponse);

/*

     MockedStatic<AwsUtils> mockAwsUtils = Mockito.mockStatic(AwsUtils.class);
     Mockito.doNothing().when(mockAwsUtils).

         .deleteFolder(any(), any(), any(), any())
    // mockAwsUtils.when(() -> AwsUtils.);
     //mockAwsUtils.when(() -> AwsUtils.getS3Client(any(), any())).thenReturn(mockS3Client);

     /*StsClient stsClient =
         StsClient.builder()
             .credentialsProvider(AnonymousCredentialsProvider.create())
             .region(Region.AWS_GLOBAL)
             .build();
     AssumeRoleWithWebIdentityRequest identityRequest = AssumeRoleWithWebIdentityRequest.builder().build();
     MockedStatic<AwsUtils> mockAwsUtils = Mockito.mockStatic(AwsUtils.class);
     mockAwsUtils
         .when(() -> AwsUtils.createWsmCredentialProvider(any(), any()))
         .thenReturn(
             StsAssumeRoleWithWebIdentityCredentialsProvider.builder().stsClient(stsClient).refreshRequest(() -> identityRequest).build());

      */

  }
}
