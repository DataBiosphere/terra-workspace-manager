package bio.terra.workspace.common;

import bio.terra.workspace.service.workspace.GcpCloudContextService;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Mock out the GcpCloudContextService where needed */
public class BaseUnitTestMockGcpCloudContextService extends BaseUnitTest {
  @MockBean private GcpCloudContextService mockGcpCloudContextService;

  public GcpCloudContextService mockGcpCloudContextService() {
    return mockGcpCloudContextService;
  }
}
