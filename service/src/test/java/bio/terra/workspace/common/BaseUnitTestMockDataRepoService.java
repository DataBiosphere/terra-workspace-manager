package bio.terra.workspace.common;

import bio.terra.workspace.service.datarepo.DataRepoService;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Mock out the DataRepoService where needed */
public class BaseUnitTestMockDataRepoService extends BaseUnitTest {
  @MockBean private DataRepoService mockDataRepoService;

  public DataRepoService mockDataRepoService() {
    return mockDataRepoService;
  }
}
