package bio.terra.workspace.pact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequestFactory;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionManager;

@Provider("wsm-provider")
@PactBroker()
public class VerifyPactsWithWds extends BaseAzureConnectedTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private WorkspaceDao workspaceDao;

  // this block of @MockBeans sprouted up as a result of playing whackamole with failed dependency
  // injection errors
  @MockBean private LandingZoneDao mockLandingZoneDao;

  @MockBean(name = "tlzTransactionManager")
  private TransactionManager mockTlzTransactionManager;

  @MockBean private JobService mockJobService;
  @MockBean private LandingZoneJobService mockLandingZoneJobService;

  // this block of @MockBeans were required to stub out layers underneath the
  // ReferencedGcpResourceController under test
  @MockBean private AuthenticatedUserRequestFactory mockAuthenticatedUserRequestFactory;
  @MockBean private SamService mockSamService;
  @MockBean private TpsApiDispatch mockTpsApiDispatch;

  @TestTemplate
  @ExtendWith(PactVerificationSpringProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @BeforeEach
  void setPactVerificationContext(PactVerificationContext context) {
    context.setTarget(new MockMvcTestTarget(mockMvc));
  }

  @State({"authenticated with the given email"})
  public void authenticatedAsAccount(Map parameters) throws InterruptedException {
    var authenticatedEmail = parameters.get("email").toString();
    var stubbedAuthenticatedRequest = new AuthenticatedUserRequest().email(authenticatedEmail);
    when(mockAuthenticatedUserRequestFactory.from(any(HttpServletRequest.class)))
        .thenReturn(stubbedAuthenticatedRequest);
    when(mockSamService.isAuthorized(
            eq(stubbedAuthenticatedRequest),
            /* iamResourceType= */ anyString(),
            /* resourceId= */ anyString(),
            /* action= */ anyString()))
        .thenReturn(true);

    // for recording the creator of the snapshot reference...
    when(mockSamService.getUserEmailFromSamAndRethrowOnInterrupt(eq(stubbedAuthenticatedRequest)))
        .thenReturn(authenticatedEmail);

    // for activity logging...
    when(mockSamService.getUserStatusInfo(eq(stubbedAuthenticatedRequest)))
        .thenReturn(new UserStatusInfo().userEmail(authenticatedEmail).userSubjectId("subjectid"));
  }

  @State({"a workspace with the given id exists"})
  public void workspaceIdExists(Map parameters) {
    var workspaceUuid = UUID.fromString(parameters.get("id").toString());
    var workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
            .userFacingId("workspaceid")
            .createdByEmail("workspace.creator@e.mail");

    if (workspaceDao.getDbWorkspace(workspaceUuid) == null) {
      workspaceDao.createWorkspaceStart(workspace.build(), "hardcoded.flight.id");
      workspaceDao.createWorkspaceSuccess(workspaceUuid, "hardcoded.flight.id");
    }
  }

  @State({"policies allowing snapshot reference creation"})
  public void allowSnapshotReferenceCreation() throws InterruptedException {
    var policyUpdateSuccessful = new TpsPaoUpdateResult().updateApplied(true);
    when(mockTpsApiDispatch.linkPao(
            /* workspaceUuid= */ any(UUID.class),
            /* sourceObjectId= */ any(UUID.class),
            any(TpsUpdateMode.class)))
        .thenReturn(policyUpdateSuccessful);
  }

  @State({"policies preventing snapshot reference creation"})
  public void preventSnapshotReferenceCreation() throws InterruptedException {
    var policiesConflicting =
        new TpsPaoUpdateResult()
            .updateApplied(false)
            .addConflictsItem(new TpsPaoConflict().name("some conflict"));
    when(mockTpsApiDispatch.linkPao(
            /* workspaceUuid= */ any(UUID.class),
            /* sourceObjectId= */ any(UUID.class),
            any(TpsUpdateMode.class)))
        .thenReturn(policiesConflicting);
  }
}
