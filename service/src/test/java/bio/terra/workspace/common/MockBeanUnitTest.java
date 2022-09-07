package bio.terra.workspace.common;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * This class provides a common set of MockBeans for unit tests. By having all tests that need mock
 * beans use the same set, they can also reuse the same application context. Each application
 * context gets its own database connection pools, so consume those connections when not in use.
 */
public class MockBeanUnitTest extends BaseUnitTest {
  @MockBean protected CrlService mockCrlService;
  @MockBean protected DataRepoService mockDataRepoService;
  @MockBean protected FeatureConfiguration mockFeatureConfiguration;
  @MockBean protected LandingZoneService mockLandingZoneService;
  @MockBean protected SamService mockSamService;
  @MockBean protected TpsApiDispatch mockTpsApiDispatch;

  public CrlService getMockCrlService() {
    return mockCrlService;
  }

  public DataRepoService getMockDataRepoService() {
    return mockDataRepoService;
  }

  public FeatureConfiguration getMockFeatureConfiguration() {
    return mockFeatureConfiguration;
  }

  public LandingZoneService getMockLandingZoneService() {
    return mockLandingZoneService;
  }

  public SamService getMockSamService() {
    return mockSamService;
  }

  public TpsApiDispatch getMockTpsApiDispatch() {
    return mockTpsApiDispatch;
  }
}
