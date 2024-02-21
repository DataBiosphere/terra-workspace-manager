package bio.terra.workspace.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.landingzone.service.landingzone.azure.model.LandingZone;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.referenced.ReferencedResourceService;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.azure.core.management.Region;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for Azure unit tests: not connected to Azure */
@Tag("azure-unit")
@ActiveProfiles({"azure-unit-test", "unit-test"})
public class BaseAzureSpringBootUnitTest extends BaseSpringBootUnitTestMocks {
  @MockBean private AzureStorageAccessService mockAzureStorageAccessService;
  @MockBean private JobApiUtils mockJobApiUtils;
  @MockBean private LandingZoneService mockLandingZoneService;
  @MockBean private WorkspaceService mockWorkspaceService;
  @MockBean private ControlledResourceMetadataManager mockControlledResourceMetadataManager;
  @MockBean private ControlledResourceService mockControlledResourceService;
  @MockBean private ReferencedResourceService mockReferencedResourceService;

  public AzureStorageAccessService mockAzureStorageAccessService() {
    return mockAzureStorageAccessService;
  }

  public JobApiUtils getMockJobApiUtils() {
    return mockJobApiUtils;
  }

  public LandingZoneService mockLandingZoneService() {
    return mockLandingZoneService;
  }

  public WorkspaceService mockWorkspaceService() {
    return mockWorkspaceService;
  }

  public ControlledResourceMetadataManager mockControlledResourceMetadataManager() {
    return mockControlledResourceMetadataManager;
  }

  public ControlledResourceService getMockControlledResourceService() {
    return mockControlledResourceService;
  }

  public ReferencedResourceService mockReferencedResourceService() {
    return mockReferencedResourceService;
  }

  public void setupMockLandingZoneRegion(Region region) {
    when(mockWorkspaceService().getWorkspace(any()))
        .thenReturn(
            WorkspaceFixtures.createDefaultMcWorkspace(
                new SpendProfileId(UUID.randomUUID().toString())));
    when(mockLandingZoneService().getLandingZonesByBillingProfile(any(), any()))
        .thenReturn(
            List.of(
                LandingZone.builder()
                    .landingZoneId(UUID.randomUUID())
                    .billingProfileId(UUID.randomUUID())
                    .definition("definition")
                    .version("1")
                    .createdDate(Instant.now().atOffset(ZoneOffset.UTC))
                    .build()));
    when(mockLandingZoneService().getLandingZoneRegion(any(), any())).thenReturn(region.name());
  }
}
