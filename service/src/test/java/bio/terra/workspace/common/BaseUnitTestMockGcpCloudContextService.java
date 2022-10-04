package bio.terra.workspace.common;

import bio.terra.workspace.service.workspace.GcpCloudContextService;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Mock out the GcpCloudContextService where needed */
public class BaseUnitTestMockGcpCloudContextService extends BaseUnitTest {
  @MockBean private GcpCloudContextService gcpCloudContextService;

  public GcpCloudContextService mockGcpCloudContextService() {
    return gcpCloudContextService;
  }
}
