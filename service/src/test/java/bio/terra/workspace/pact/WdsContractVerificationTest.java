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
import bio.terra.policy.model.TpsPaoDescription;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.workspace.common.BaseAzureUnitTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Map;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionManager;

@Tag("pact-verification")
@Provider("workspacemanager") // should match the terra chart name
@PactBroker()
public class WdsContractVerificationTest extends BaseAzureUnitTest {
  @Autowired private MockMvc mockMvc;

  // this block of @MockBeans sprouted up as a result of playing whackamole with failed dependency
  // injection errors, mostly due to NPEs through by `DataSourceInitializer`
  @MockBean
  private LandingZoneDao
      mockLandingZoneDao; // required for dependency chain up to azureConnectedTestUtils

  @MockBean(name = "tlzTransactionManager")
  private TransactionManager
      mockTlzTransactionManager; // this bean fails on missing datasource otherwise

  @MockBean private JobService mockJobService; // for JobService.initialize call on startup

  @MockBean
  private LandingZoneJobService mockLandingZoneJobService; // for LandingZone.main call on startup

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
  public void authenticatedAsAccount(Map<?, ?> parameters) throws InterruptedException {
    var authenticatedEmail = parameters.get("email").toString();

    // for recording the creator of the snapshot reference...
    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn(authenticatedEmail);

    // for activity logging...
    when(mockSamService().getUserStatusInfo(any(AuthenticatedUserRequest.class)))
        .thenReturn(new UserStatusInfo().userEmail(authenticatedEmail).userSubjectId("subjectid"));
  }

  @State({"a workspace with the given id exists"})
  public void workspaceIdExists(Map<?, ?> parameters) {
    var workspaceUuid = UUID.fromString(parameters.get("id").toString());
    when(mockWorkspaceService()
            .validateWorkspaceAndAction(
                any(AuthenticatedUserRequest.class), eq(workspaceUuid), /* action= */ anyString()))
        .thenReturn(
            Workspace.builder()
                .workspaceId(workspaceUuid)
                .workspaceStage(WorkspaceStage.RAWLS_WORKSPACE)
                .userFacingId("workspaceid")
                .createdByEmail("workspace.creator@e.mail")
                .build());
  }

  @State({"policies allowing snapshot reference creation"})
  public void allowSnapshotReferenceCreation() throws InterruptedException {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    var policyUpdateSuccessful = new TpsPaoUpdateResult().updateApplied(true);
    when(mockWorkspaceService()
            .linkPolicies(
                /* workspaceUuid= */ any(UUID.class),
                any(TpsPaoDescription.class),
                any(TpsUpdateMode.class),
                any(AuthenticatedUserRequest.class)))
        .thenReturn(policyUpdateSuccessful);

    when(mockReferencedResourceService()
            .createReferenceResource(
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
    when(mockWorkspaceService()
            .linkPolicies(
                /* workspaceUuid= */ any(UUID.class),
                any(TpsPaoDescription.class),
                any(TpsUpdateMode.class),
                any(AuthenticatedUserRequest.class)))
        .thenReturn(policiesConflicting);
  }
}
