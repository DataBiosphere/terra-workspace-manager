package bio.terra.workspace.service.workspace.flight.create.azure;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.generated.model.ApiAccessScope;
import bio.terra.workspace.generated.model.ApiAzureIpCreationParameters;
import bio.terra.workspace.generated.model.ApiManagedBy;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledAzureIpResource;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureIpStep;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.spendprofile.SpendConnectedTestUtils;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateAzureIpTest extends BaseAzureTest {
  private static final Duration STAIRWAY_FLIGHT_TIMEOUT = Duration.ofMinutes(10);

  @Mock private FlightContext mockFlightContext;
  @Autowired private WorkspaceService workspaceService;
  @Autowired private CrlService crl;
  @Autowired private JobService jobService;
  @Autowired private SpendConnectedTestUtils spendUtils;
  @Autowired private SamService samService;
  @Autowired private AzureTestUtils azureTestUtils;
  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @Mock private AzureConfiguration azureConfiguration;
  @Autowired private ControlledResourceService controlledResourceService;

  @BeforeEach
  public void setup() {
    // TODO: these currently need to be manually sourced from here:
    // https://docs.google.com/document/d/1Ye-CaNijWX7rA6HHV-8AhRRmd1dufG8QQGoCssksPcE/edit?resourcekey=0-gmRRxVojB4hXV8_-524F4A#heading=h.lw7ji3aqppay
    when(azureConfiguration.getManagedAppClientId()).thenReturn("stub");
    when(azureConfiguration.getManagedAppClientSecret()).thenReturn("stub");
    when(azureConfiguration.getManagedAppTenantId()).thenReturn("stub");
  }

  // This test is similar to CreateAzureIpStepTest, but it actually exercises the underlying
  // CRL/azure API
  // Will fail if the azure call fails
  // disabled because it will fail without proper creds in setup
  @Test
  @Disabled
  void createAzureIpStep() throws Exception {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);
    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();

    AzureCloudContext azureCloudContext = azureTestUtils.getAzureCloudContext();

    // step initialization
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    CreateAzureIpStep createAzureIpStep =
        new CreateAzureIpStep(
            azureConfiguration,
            azureCloudContext,
            crl,
            ControlledResourceFixtures.getAzureIp(creationParameters.getName()),
            workspaceService);

    // setup flight map
    final FlightMap inputFlightMap = azureTestUtils.createInputParameters(workspaceId, userRequest);
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATION_PARAMETERS, creationParameters);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final StepResult stepResult = createAzureIpStep.doStep(mockFlightContext);
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // We could verify that the controlled resource is properly created and in the WSM DB here
    // However, that's more testing WSM than the underlying azure calls
    // This test will error out if any of the underlying azure APIs has an error
  }

  // TODO: mock SAM complains about this
  // TODO: wire up the appropriate azure config to the components beneath controlledResourceService
  // in callstack
  @Test
  @Disabled
  void createAzureIpControlledResource() {
    UUID workspaceId = azureTestUtils.createWorkspace(workspaceService);

    AuthenticatedUserRequest userRequest = azureTestUtils.defaultUserAuthRequest();
    final ApiAzureIpCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureIpCreationParameters();

    ControlledAzureIpResource resource =
        ControlledAzureIpResource.builder()
            .workspaceId(workspaceId)
            .resourceId(UUID.randomUUID())
            .name("testName")
            .description("testDesc")
            .cloningInstructions(CloningInstructions.COPY_RESOURCE)
            .assignedUser(userRequest.getEmail())
            .accessScope(AccessScopeType.fromApi(ApiAccessScope.PRIVATE_ACCESS))
            .managedBy(ManagedByType.fromApi(ApiManagedBy.USER))
            .ipName(creationParameters.getName())
            .region(Region.fromName(creationParameters.getRegion()))
            .build();

    List<ControlledResourceIamRole> privateRoles =
        new ArrayList<>(); // ControlledAzureResourceApiController.privateRolesFromBody(body.getCommon());
    controlledResourceService.createIp(resource, creationParameters, privateRoles, userRequest);
  }
}
