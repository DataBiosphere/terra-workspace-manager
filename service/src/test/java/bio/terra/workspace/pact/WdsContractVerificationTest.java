package bio.terra.workspace.pact;

import static org.mockito.ArgumentMatchers.any;
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
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseUnitTestMocks;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.danglingresource.DanglingResourceCleanupService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({
  "unit-test" /* disable some unnecessary dependencies that would otherwise require mocking */,
  "pact-test" /* set pact broker URL */
})
@Tag("pact-verification")
@Provider("workspacemanager") // should match the terra chart name
@PactBroker()
public class WdsContractVerificationTest extends BaseUnitTestMocks {
  @Autowired private MockMvc mockMvc;

  // Mocked out to prevent an error with missing service credentials
  @MockBean private DanglingResourceCleanupService unusedDanglingResourceCleanupService;

  // needed to mock behavior in this test
  @MockBean private WorkspaceDao mockWorkspaceDao;
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;
  @MockBean private ReferencedResourceService mockReferencedResourceService;

  @PactBrokerConsumerVersionSelectors
  public static SelectorBuilder consumerVersionSelectors() {
    // Select consumer pacts published from default branch or pacts marked as deployed or released.
    // If you wish to pick up Pacts from a consumer's feature branch for development purposes or PR
    // runs, and your consumer is publishing such Pacts under their feature branch name, you can add
    // the following to the SelectorBuilder:
    //   .branch("consumer-feature-branch-name")
    return new SelectorBuilder().mainBranch().deployedOrReleased();
  }

  @BeforeEach
  void setPactVerificationContext(PactVerificationContext context) {
    context.setTarget(new MockMvcTestTarget(mockMvc));
  }

  @TestTemplate
  @ExtendWith(PactVerificationSpringProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @State("authenticated with the given email")
  public void authenticatedAsAccount(Map<?, ?> parameters) throws InterruptedException {
    var authenticatedEmail = parameters.get("email").toString();
    var stubbedAuthenticatedRequest = new AuthenticatedUserRequest().email(authenticatedEmail);
    when(mockAuthenticatedUserRequestFactory.from(any(HttpServletRequest.class)))
        .thenReturn(stubbedAuthenticatedRequest);
    when(mockSamService()
            .isAuthorized(
                eq(stubbedAuthenticatedRequest),
                /* iamResourceType= */ anyString(),
                /* resourceId= */ anyString(),
                /* action= */ anyString()))
        .thenReturn(true);

    // for recording the creator of the snapshot reference...
    when(mockSamService().getUserEmailFromSamAndRethrowOnInterrupt(eq(stubbedAuthenticatedRequest)))
        .thenReturn(authenticatedEmail);

    // for activity logging...
    when(mockSamService().getUserStatusInfo(eq(stubbedAuthenticatedRequest)))
        .thenReturn(new UserStatusInfo().userEmail(authenticatedEmail).userSubjectId("subjectid"));
  }

  @State({"a workspace with the given id exists"})
  public void workspaceIdExists(Map<?, ?> parameters) {
    var workspaceUuid = UUID.fromString(parameters.get("id").toString());
    when(mockWorkspaceDao.getWorkspace(eq(workspaceUuid)))
        .thenReturn(
            Workspace.builder()
                .workspaceId(workspaceUuid)
                .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
                .userFacingId("workspaceid")
                .createdByEmail("workspace.creator@e.mail")
                .state(WsmResourceState.READY)
                .build());
  }

  @State({"policies allowing snapshot reference creation"})
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
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @State({"policies preventing snapshot reference creation"})
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
}
