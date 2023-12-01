package bio.terra.workspace.pact;

import static bio.terra.workspace.service.resource.model.WsmResourceFamily.AZURE_STORAGE_CONTAINER;
import static bio.terra.workspace.service.resource.model.WsmResourceFamily.DATA_REPO_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseUnitTestMocks;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbCloudContext;
import bio.terra.workspace.service.danglingresource.DanglingResourceCleanupService;
import bio.terra.workspace.service.iam.AuthHeaderKeys;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureSasBundle;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.SasTokenOptions;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import com.azure.core.management.Region;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({
  "unit-test" /* disable some unnecessary dependencies that would otherwise require mocking */,
  "pact-test" /* set Pact Broker URL */
})
@Tag("pact-verification")
@Provider("workspacemanager") // should match the terra chart name
@PactBroker(enablePendingPacts = "true", providerTags = "main")
public class WdsContractVerificationTest extends BaseUnitTestMocks {
  // a randomly generated UUID that spans multiple stateful contract calls
  private static final UUID STORAGE_CONTAINER_RESOURCE_ID = UUID.randomUUID();
  private static final String CONSUMER_BRANCH = System.getenv("CONSUMER_BRANCH");

  @Autowired private MockMvc mockMvc;

  // Mocked out to prevent an error with missing service credentials
  @MockBean(name = "postSetupInitialization")
  private SmartInitializingSingleton unusedSmartInitializingSingleton;

  // Mocked out to prevent error trying to clean up nonexistent tables
  @MockBean private DanglingResourceCleanupService unusedDanglingResourceCleanupService;

  @MockBean private WorkspaceActivityLogDao unusedWorkspaceActivityLogDao;

  // needed to mock behavior in this test
  @MockBean private WorkspaceDao mockWorkspaceDao;
  @MockBean private ResourceDao mockResourceDao;
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;
  @MockBean private ReferencedResourceService mockReferencedResourceService;
  @MockBean private AzureStorageAccessService mockAzureStorageAccessService;

  @PactBrokerConsumerVersionSelectors
  public static SelectorBuilder consumerVersionSelectors() {
    // The following match condition basically says
    // If verification is triggered by Pact Broker webhook due to consumer pact change, verify only
    // the changed pact.
    // Otherwise, this is a PR, verify all consumer pacts in Pact Broker marked with a deployment
    // tag (e.g. dev, alpha).
    if (StringUtils.isBlank(CONSUMER_BRANCH)) {
      return new SelectorBuilder().mainBranch().deployedOrReleased();
    } else {
      return new SelectorBuilder().branch(CONSUMER_BRANCH);
    }
  }

  @BeforeEach
  void setPactVerificationContext(PactVerificationContext context) {
    context.setTarget(new MockMvcTestTarget(mockMvc));
    when(mockSamService().getWsmServiceAccountToken()).thenReturn("fakewsmserviceaccounttoken");
  }

  @TestTemplate
  @ExtendWith(PactVerificationSpringProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  private AuthenticatedUserRequest stubAuthenticatedRequest(
      String authenticatedEmail, HttpServletRequest request) {
    // TODO: figure out how to avoid reimplementing this logic scavenged from
    //   ProxiedAuthenticatedUserRequestFactory
    String authHeader = request.getHeader(AuthHeaderKeys.AUTHORIZATION.getKeyName());
    String bearerToken = StringUtils.substring(authHeader, "Bearer ".length());
    return new AuthenticatedUserRequest(
        authenticatedEmail, "fakesubjectid", Optional.of(bearerToken));
  }

  @State("authenticated with the given {email}")
  public void authenticatedAsEmail(Map<?, ?> pactState) throws InterruptedException {
    var email = pactState.get("email").toString();
    when(mockAuthenticatedUserRequestFactory.from(any(HttpServletRequest.class)))
        .thenAnswer(
            invocation -> stubAuthenticatedRequest(email, invocation.getArgument(/* index= */ 0)));

    // return true only if the email matches what we stubbed
    when(mockSamService()
            .isAuthorized(
                any(AuthenticatedUserRequest.class), // invocation index = 0
                /* iamResourceType= */ anyString(),
                /* resourceId= */ anyString(),
                /* action= */ anyString()))
        .thenAnswer(
            invocation -> {
              getExpectedRequestOrFail(invocation.getArgument(/* index= */ 0), email);
              return true; // return true if the request we received matches the authenticated email
            });

    // for activity logging while creating a snapshot reference
    when(mockSamService().getUserStatusInfo(any(AuthenticatedUserRequest.class)))
        .thenAnswer(
            invocation -> {
              var request = getExpectedRequestOrFail(invocation.getArgument(/* index= */ 0), email);
              return new UserStatusInfo()
                  .userEmail(request.getEmail())
                  .userSubjectId(request.getSubjectId());
            });

    // for storing the creator email while creating a snapshot reference
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenAnswer(
            invocation ->
                getExpectedRequestOrFail(invocation.getArgument(/* index= */ 0), email).getEmail());

    // for activity logging while creating Azure storage access token
    when(mockSamService().getSamUser(any(AuthenticatedUserRequest.class)))
        .thenAnswer(
            invocation -> {
              var request = getExpectedRequestOrFail(invocation.getArgument(/* index= */ 0), email);
              return new SamUser(
                  request.getEmail(),
                  request.getSubjectId(),
                  new BearerToken(request.getRequiredToken()));
            });
  }

  private static AuthenticatedUserRequest getExpectedRequestOrFail(
      AuthenticatedUserRequest request, String expectedEmail) {
    if (request.getEmail().equals(expectedEmail)) {
      return request;
    }

    throw new IllegalArgumentException(
        String.format(
            "Expected authenticated request for %s, got %s", expectedEmail, request.getEmail()));
  }

  @State("a workspace with the given {id} exists")
  public void workspaceIdExists(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "id");
    when(mockWorkspaceDao.getWorkspace(eq(workspaceUuid)))
        .thenReturn(
            Workspace.builder()
                .workspaceId(workspaceUuid)
                .workspaceStage(WorkspaceStage.MC_WORKSPACE)
                .userFacingId("userfacingid")
                .createdByEmail("workspace.creator@e.mail")
                .state(WsmResourceState.READY)
                .build());
  }

  @State("an Azure cloud context exists for the given {workspace_id}")
  public void workspaceIsAzure(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "workspace_id");
    when(mockWorkspaceDao.getCloudContext(workspaceUuid, CloudPlatform.AZURE))
        .thenReturn(
            Optional.of(
                new DbCloudContext()
                    .cloudPlatform(CloudPlatform.AZURE)
                    .state(WsmResourceState.READY)));
  }

  @State({
    "no Azure storage container resources exist for the given {workspace_id}",
    "no snapshot resources exist for the given {workspace_id}"
  })
  public void noResourcesExist(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "workspace_id");
    when(mockResourceDao.enumerateResources(
            eq(workspaceUuid),
            any(WsmResourceFamily.class),
            any(StewardshipType.class),
            /* offset= */ anyInt(),
            /* limit= */ anyInt()))
        .thenReturn(Collections.emptyList());
  }

  @State("an Azure storage container resource exists for the given {workspace_id}")
  public Map<String, String> storageContainerExists(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "workspace_id");
    String storageContainerName = String.format("sc-%s", workspaceUuid);
    ControlledAzureStorageContainerResource stubbedStorageContainerResource =
        ControlledAzureStorageContainerResource.builder()
            .storageContainerName(storageContainerName)
            .common(
                ControlledResourceFields.builder()
                    .name(storageContainerName)
                    .workspaceUuid(workspaceUuid)
                    .createdByEmail("storage.container.creator@e.mail")
                    .managedBy(ManagedByType.MANAGED_BY_USER)
                    .accessScope(AccessScopeType.ACCESS_SCOPE_SHARED)
                    .resourceId(STORAGE_CONTAINER_RESOURCE_ID)
                    .cloningInstructions(CloningInstructions.COPY_NOTHING)
                    .region(Region.US_SOUTH_CENTRAL.name())
                    .build())
            .build();
    when(mockResourceDao.getResource(eq(workspaceUuid), eq(STORAGE_CONTAINER_RESOURCE_ID)))
        .thenReturn(stubbedStorageContainerResource);
    when(mockResourceDao.enumerateResources(
            eq(workspaceUuid),
            eq(AZURE_STORAGE_CONTAINER),
            /* stewardshipType= */ any(), // is null for storage container enumeration
            /* offset= */ anyInt(),
            /* limit= */ anyInt()))
        .thenReturn(List.of(stubbedStorageContainerResource));
    return Map.of("storageContainerResourceId", STORAGE_CONTAINER_RESOURCE_ID.toString());
  }

  @State("{num_snapshots} snapshots exist for the given {workspace_id}")
  public void snapshotsExist(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "workspace_id");
    var numSnapshots = getInteger(pactState, "num_snapshots");

    List<WsmResource> stubbedResources =
        IntStream.range(0, numSnapshots)
            .mapToObj(
                unused ->
                    (WsmResource)
                        ReferencedDataRepoSnapshotResource.builder()
                            .snapshotId(UUID.randomUUID().toString())
                            .instanceName("someinstancename")
                            .wsmResourceFields(
                                WsmResourceFields.builder()
                                    .resourceId(UUID.randomUUID())
                                    .workspaceUuid(workspaceUuid)
                                    .name("snapshotName")
                                    .cloningInstructions(CloningInstructions.COPY_REFERENCE)
                                    .createdByEmail("snapshot.creator@e.mail")
                                    .build())
                            .build())
            .toList();

    when(mockResourceDao.enumerateResources(
            eq(workspaceUuid),
            eq(DATA_REPO_SNAPSHOT),
            any(StewardshipType.class),
            /* offset= */ anyInt(),
            /* limit= */ anyInt()))
        .thenReturn(stubbedResources);
  }

  @State("policies allowing snapshot reference creation")
  public void allowSnapshotReferenceCreation() throws InterruptedException {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    var policyUpdateSuccessful = new TpsPaoUpdateResult().updateApplied(true);
    when(mockTpsApiDispatch()
            .linkPao(
                /* workspaceUuid= */ any(UUID.class),
                /* sourceObjectId= */ any(UUID.class),
                any(TpsUpdateMode.class)))
        .thenReturn(policyUpdateSuccessful);

    when(mockReferencedResourceService.createReferenceResource(
            any(ReferencedResource.class), any(AuthenticatedUserRequest.class)))
        // just echo back the resource that the controller attempted to create to simulate success
        .thenAnswer(invocation -> invocation.getArgument(/* index= */ 0));
  }

  @State("policies preventing snapshot reference creation")
  public void preventSnapshotReferenceCreation() throws InterruptedException {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    var policiesConflicting =
        new TpsPaoUpdateResult()
            .updateApplied(false)
            .addConflictsItem(new TpsPaoConflict().name("some conflict"));
    when(mockTpsApiDispatch()
            .linkPao(
                /* workspaceUuid= */ any(UUID.class),
                /* sourceObjectId= */ any(UUID.class),
                any(TpsUpdateMode.class)))
        .thenReturn(policiesConflicting);

    when(mockReferencedResourceService.createReferenceResource(
            any(ReferencedResource.class), any(AuthenticatedUserRequest.class)))
        // just throw if an unexpected attempt is made to create a referenced resource
        .thenThrow(
            new RuntimeException(
                "mockTpsApiDispatch() should have prevented #createReferenceResource from being reached."));
  }

  @State("no permission to create an azure storage container sas token")
  public void blockStorageContainerSasTokenCreation() {
    when(mockAzureStorageAccessService.createAzureStorageContainerSasToken(
            /* workspaceUuid= */ any(UUID.class),
            /* storageContainerUuid= */ any(UUID.class),
            any(AuthenticatedUserRequest.class),
            any(SasTokenOptions.class)))
        .thenThrow(
            new ForbiddenException(
                String.format(
                    "User is not authorized to get a SAS token for container %s",
                    STORAGE_CONTAINER_RESOURCE_ID)));
  }

  @State("permission to create an azure storage container sas token for the given {workspace_id}")
  public void allowStorageContainerSasTokenCreation(Map<?, ?> pactState) {
    var workspaceUuid = getUuid(pactState, "workspace_id");
    when(mockAzureStorageAccessService.createAzureStorageContainerSasToken(
            eq(workspaceUuid),
            eq(STORAGE_CONTAINER_RESOURCE_ID),
            any(AuthenticatedUserRequest.class),
            any(SasTokenOptions.class)))
        .thenReturn(
            new AzureSasBundle("somesastoken", "https://some.sas.url/something", "somesha"));
  }

  private static void assertKeyPresent(Map<?, ?> map, String key) {
    assertTrue(
        map.containsKey(key), String.format("Expected %s in map, found %s", key, map.keySet()));
  }

  // pact state helpers
  private static String getString(Map<?, ?> map, String key) {
    assertKeyPresent(map, key);
    return map.get(key).toString();
  }

  private static UUID getUuid(Map<?, ?> map, String key) {
    return UUID.fromString(getString(map, key));
  }

  private static Integer getInteger(Map<?, ?> map, String key) {
    return Integer.parseInt(getString(map, key));
  }
}
