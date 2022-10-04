package bio.terra.workspace.common;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@Tag("azure-unit")
@ActiveProfiles({"azure-unit-test", "unit-test"})
public class BaseAzureUnitTest extends BaseUnitTestMocks {
  @MockBean private AzureStorageAccessService mockAzureStorageAccessService;
  @MockBean private JobService mockJobService;
  @MockBean private LandingZoneService mockLandingZoneService;
  @MockBean private WorkspaceService mockWorkspaceService;

  public AzureStorageAccessService mockAzureStorageAccessService() {
    return mockAzureStorageAccessService;
  }

  public JobService mockJobService() {
    return mockJobService;
  }

  public LandingZoneService mockLandingZoneService() {
    return mockLandingZoneService;
  }

  public WorkspaceService mockWorkspaceService() {
    return mockWorkspaceService;
  }
}
