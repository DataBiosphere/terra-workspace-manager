package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.CreateAiNotebookInstanceStep.DEFAULT_POST_STARTUP_SCRIPT;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_LOCATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.notebooks.AIPlatformNotebooksCow;
import bio.terra.cloudres.google.notebooks.InstanceName;
import bio.terra.common.exception.ForbiddenException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.app.configuration.external.CliConfiguration;
import bio.terra.workspace.app.configuration.external.VersionConfiguration;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceAcceleratorConfig;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceContainerImage;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.generated.model.ApiGcpAiNotebookInstanceVmImage;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.ReservedMetadataKeyException;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.services.notebooks.v1.model.Instance;
import com.google.api.services.notebooks.v1.model.Operation;
import com.google.common.collect.ImmutableList;
import com.google.rpc.Code;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

public class CreateAiNotebookInstanceStepTest extends BaseUnitTest {

  private static final List<String> SA_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/cloud-platform",
          "https://www.googleapis.com/auth/userinfo.email",
          "https://www.googleapis.com/auth/userinfo.profile");
  private static final String WORKSPACE_ID = "my-workspce-ufid";
  private static final String SERVER_ID = "test-server";
  private static final String NOTEBOOK_DISABLE_ROOT_KEY = "notebook-disable-root";

  //  @Mock private
  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private GcpCloudContextService mockGcpCloudContextService;

  @Mock private AIPlatformNotebooksCow mockAIPlatformNotebookCow;
  @Mock private AIPlatformNotebooksCow.Instances mockInstances;
  @Mock private AIPlatformNotebooksCow.Instances.Create mockInstancesCreate;

  @Mock private AIPlatformNotebooksCow.Operations mockOperations;
  @Mock private OperationCow<Operation> mockOperationCow;
  @Mock private OperationCow.OperationAdapter<Operation> mockOperationAdapter;
  @Mock private OperationCow.OperationAdapter.StatusAdapter mockOperationStatusAdapter;
  @Mock private Operation mockOperation;

  private static final String FAKE_PROJECT_ID = "fakeprojectid";

  // The permission denied code includes cases of the "quota exceeded limit" error.
  @Test
  public void create_throwsDuringOperationPolling_Permission_Denied() throws IOException {
    // Create the mock AIPlatform Notebook cow,
    Mockito.doReturn(mockAIPlatformNotebookCow).when(mockCrlService).getAIPlatformNotebooksCow();
    when(mockAIPlatformNotebookCow.instances()).thenReturn(mockInstances);
    when(mockInstances.create(any(InstanceName.class), any(Instance.class)))
        .thenReturn(mockInstancesCreate);
    when(mockInstancesCreate.execute()).thenReturn(mockOperation);

    when(mockAIPlatformNotebookCow.operations()).thenReturn(mockOperations);

    // And mock the OperationCow.
    when(mockOperations.operationCow(any(Operation.class))).thenReturn(mockOperationCow);
    when(mockOperationCow.getOperationAdapter()).thenReturn(mockOperationAdapter);
    when(mockOperationAdapter.getDone()).thenReturn(true);
    when(mockGcpCloudContextService.getRequiredGcpProject(any())).thenReturn(FAKE_PROJECT_ID);

    // Create fake configs for the create notebook step.
    CliConfiguration fakeCliConfiguration = new CliConfiguration();
    fakeCliConfiguration.setServerName("fake-server-name");

    VersionConfiguration fakeVersionConfig = new VersionConfiguration(new MockEnvironment());
    fakeVersionConfig.setGitHash("fake-git-hash");

    // Mock the create step.
    final CreateAiNotebookInstanceStep createNotebookStep =
        new CreateAiNotebookInstanceStep(
            ControlledResourceFixtures.makeDefaultAiNotebookInstance().build(),
            "fake-pet",
            "fake-workspace-user-facing-id",
            mockCrlService,
            fakeCliConfiguration,
            fakeVersionConfig);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS,
        ControlledResourceFixtures.defaultNotebookCreationParameters());
    inputFlightMap.makeImmutable();
    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();

    final FlightMap workingFlightMap = new FlightMap();
    GcpCloudContext fakeCloudContext = new GcpCloudContext(FAKE_PROJECT_ID);

    workingFlightMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.GCP_CLOUD_CONTEXT, fakeCloudContext);
    workingFlightMap.put(CREATE_NOTEBOOK_LOCATION, "fake-location");
    doReturn(workingFlightMap).when(mockFlightContext).getWorkingMap();

    // Mock the permission error thrown when polling the error (e.g., quota exceeded limit)
    when(mockOperationAdapter.getError()).thenReturn(mockOperationStatusAdapter);
    when(mockOperationStatusAdapter.getCode()).thenReturn(Code.PERMISSION_DENIED_VALUE);
    assertThrows(ForbiddenException.class, () -> createNotebookStep.doStep(mockFlightContext));
  }

  @Test
  public void setFields() {
    var creationParameters =
        new ApiGcpAiNotebookInstanceCreationParameters()
            .postStartupScript("script.sh")
            .machineType("machine-type")
            .installGpuDriver(true)
            .customGpuDriverPath("custom-path")
            .bootDiskType("boot-disk-type")
            .bootDiskSizeGb(111L)
            .dataDiskType("data-disk-type")
            .dataDiskSizeGb(222L)
            .metadata(Map.of("metadata-key", "metadata-value"))
            .acceleratorConfig(
                new ApiGcpAiNotebookInstanceAcceleratorConfig()
                    .coreCount(4L)
                    .type("accelerator-type"))
            .vmImage(
                new ApiGcpAiNotebookInstanceVmImage()
                    .projectId("project-id")
                    .imageFamily("image-family")
                    .imageName("image-name"))
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("repository").tag("tag"));
    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            creationParameters, "foo@bar.com", WORKSPACE_ID, SERVER_ID, new Instance(), "main");
    assertEquals("script.sh", instance.getPostStartupScript());
    assertTrue(instance.getInstallGpuDriver());
    assertEquals("custom-path", instance.getCustomGpuDriverPath());
    assertEquals("boot-disk-type", instance.getBootDiskType());
    assertEquals(111L, instance.getBootDiskSizeGb());
    assertEquals("data-disk-type", instance.getDataDiskType());
    assertEquals(222L, instance.getDataDiskSizeGb());
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(5));
    assertThat(instance.getMetadata(), Matchers.hasEntry("metadata-key", "metadata-value"));
    assertThat(instance.getMetadata(), Matchers.hasEntry(NOTEBOOK_DISABLE_ROOT_KEY, "true"));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccount());
    assertEquals(SA_SCOPES, instance.getServiceAccountScopes());
    assertEquals(4L, instance.getAcceleratorConfig().getCoreCount());
    assertEquals("accelerator-type", instance.getAcceleratorConfig().getType());
    assertEquals("project-id", instance.getVmImage().getProject());
    assertEquals("image-family", instance.getVmImage().getImageFamily());
    assertEquals("image-name", instance.getVmImage().getImageName());
    assertEquals("repository", instance.getContainerImage().getRepository());
    assertEquals("tag", instance.getContainerImage().getTag());
  }

  @Test
  public void setFieldsNoFields() {
    var localBranch = "monkey";
    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            new ApiGcpAiNotebookInstanceCreationParameters(),
            "foo@bar.com",
            WORKSPACE_ID,
            SERVER_ID,
            new Instance(),
            localBranch);
    assertThat(instance.getMetadata(), Matchers.aMapWithSize(3));
    assertDefaultMetadata(instance);
    assertEquals("foo@bar.com", instance.getServiceAccount());
    assertEquals(SA_SCOPES, instance.getServiceAccountScopes());
    assertEquals(
        String.format(DEFAULT_POST_STARTUP_SCRIPT, localBranch), instance.getPostStartupScript());
    assertFalse(instance.getMetadata().containsKey(NOTEBOOK_DISABLE_ROOT_KEY));
  }

  private void assertDefaultMetadata(Instance instance) {
    // git secrets gets a false positive if 'service_account' is double quoted.
    assertThat(instance.getMetadata(), Matchers.hasEntry("proxy-mode", "service_" + "account"));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-workspace-id", WORKSPACE_ID));
    assertThat(instance.getMetadata(), Matchers.hasEntry("terra-cli-server", SERVER_ID));
  }

  @Test
  public void setFields_userSpecifiedNotebookDisableRootToFalse() {
    var creationParameters =
        new ApiGcpAiNotebookInstanceCreationParameters()
            .metadata(Map.of(NOTEBOOK_DISABLE_ROOT_KEY, "false"))
            .containerImage(
                new ApiGcpAiNotebookInstanceContainerImage().repository("repository").tag("tag"));

    Instance instance =
        CreateAiNotebookInstanceStep.setFields(
            creationParameters, "foo@bar.com", WORKSPACE_ID, SERVER_ID, new Instance(), "main");
    assertThat(instance.getMetadata(), Matchers.hasEntry(NOTEBOOK_DISABLE_ROOT_KEY, "false"));
  }

  @Test
  public void setFieldsThrowsForReservedMetadataKeys() {
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-workspace-id" is a reserved metadata key.
                    .metadata(Map.of("terra-workspace-id", "fakeworkspaceid")),
                "foo@bar.com",
                "workspaceId",
                "server-id",
                new Instance(),
                "main"));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "terra-cli-server" is a reserved metadata key.
                    .metadata(Map.of("terra-cli-server", "fakeserver")),
                "foo@bar.com",
                "workspaceId",
                "server-id",
                new Instance(),
                "main"));
    assertThrows(
        ReservedMetadataKeyException.class,
        () ->
            CreateAiNotebookInstanceStep.setFields(
                new ApiGcpAiNotebookInstanceCreationParameters()
                    // "proxy-mode" is a reserved metadata key.
                    .metadata(Map.of("proxy-mode", "mail")),
                "foo@bar.com",
                "workspaceId",
                "server-id",
                new Instance(),
                "main"));
  }
}
