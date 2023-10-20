package bio.terra.workspace.pact;

import static org.mockito.ArgumentMatchers.any;
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
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceFields;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.resource.referenced.cloud.any.datareposnapshot.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionManager;

@Provider("wsm-provider")
@PactBroker()
public class VerifyPactsWithWds extends BaseAzureUnitTest {

  @Autowired private MockMvc mockMvc;

  // this block of @MockBeans sprouted up as a result of playing whackamole with failed dependency
  // injection errors
  @MockBean private LandingZoneDao mockLandingZoneDao;

  @MockBean(name = "tlzTransactionManager")
  private TransactionManager mockTlzTransactionManager;

  @MockBean private JobService mockJobService;
  @MockBean private LandingZoneJobService mockLandingZoneJobService;

  // This @MockBean is needed to avoid having FK errors; maybe can be avoided by _not_ mocking out
  // workspaceService
  @MockBean private ReferencedResourceService mockReferencedResourceService;

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
  public void authenticatedAsAccount(Map parameters) {
    var email = parameters.get("email").toString();

    when(mockSamService()
            .getUserEmailFromSamAndRethrowOnInterrupt(any(AuthenticatedUserRequest.class)))
        .thenReturn(email);
  }

  @State({"a workspace with the given id exists"})
  public void workspaceIdExists(Map parameters) {
    var workspaceUuid = UUID.fromString(parameters.get("id").toString());

    var mockWorkspace =
        WorkspaceFixtures.buildWorkspace(workspaceUuid, WorkspaceStage.RAWLS_WORKSPACE);

    when(mockWorkspaceService()
            .validateWorkspaceAndAction(
                any(AuthenticatedUserRequest.class),
                eq(workspaceUuid),
                eq(SamWorkspaceAction.CREATE_REFERENCE)))
        .thenReturn(mockWorkspace);
  }

  @State({"policies allowing snapshot reference creation"})
  public void allowSnapshotReferenceCreation() {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    var policyUpdateSuccessful = new TpsPaoUpdateResult().updateApplied(true);
    when(mockWorkspaceService()
            .linkPolicies(
                any(UUID.class),
                any(TpsPaoDescription.class),
                any(TpsUpdateMode.class),
                any(AuthenticatedUserRequest.class)))
        .thenReturn(policyUpdateSuccessful);
    // TODO: seems like some brittle mocking that is completely divorced from the actual request
    //   received, maybe a more stateful/connected test would serve to provide more realistic
    //   coverage, or possibly this mock is happening in the wrong place
    when(mockReferencedResourceService.createReferenceResource(
            any(ReferencedResource.class), any(AuthenticatedUserRequest.class)))
        .thenReturn(
            new ReferencedDataRepoSnapshotResource(
                WsmResourceFields.builder()
                    .workspaceUuid(UUID.randomUUID())
                    .resourceId(UUID.randomUUID())
                    .name("snapshotName")
                    .cloningInstructions(
                        CloningInstructions
                            .COPY_REFERENCE) // TODO: is this actually the right enum?
                    .createdByEmail("TODO@e.mail")
                    .build(),
                "snapshotInstanceName",
                "snapshotId"));
  }

  @State({"policies preventing snapshot reference creation"})
  public void preventSnapshotReferenceCreation() {
    when(mockFeatureConfiguration().isTpsEnabled()).thenReturn(true);
    var policiesConflicting =
        new TpsPaoUpdateResult()
            .updateApplied(false)
            .addConflictsItem(new TpsPaoConflict().name("something something"));
    when(mockWorkspaceService()
            .linkPolicies(
                any(UUID.class),
                any(TpsPaoDescription.class),
                any(TpsUpdateMode.class),
                any(AuthenticatedUserRequest.class)))
        .thenReturn(policiesConflicting);
  }

  @Autowired private Environment environment;
}
