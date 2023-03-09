package bio.terra.workspace.common;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@Tag("aws-unit")
@ActiveProfiles({"aws-unit-test", "unit-test"})
public class BaseAwsUnitTest extends BaseUnitTestMocks {
  @MockBean private AzureStorageAccessService mockAzureStorageAccessService;
  @MockBean private JobService mockJobService;
  @MockBean private JobApiUtils mockJobApiUtils;
  @MockBean private LandingZoneService mockLandingZoneService;
  @MockBean private WorkspaceService mockWorkspaceService;
}
