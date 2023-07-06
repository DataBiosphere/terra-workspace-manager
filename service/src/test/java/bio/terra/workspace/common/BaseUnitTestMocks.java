package bio.terra.workspace.common;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.features.FeatureService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import org.springframework.boot.test.mock.mockito.MockBean;

/*
 * Mock beans used by (or not conflicting with) all unit tests
 */
public class BaseUnitTestMocks extends BaseTest {
  @MockBean private CrlService mockCrlService;
  @MockBean private FeatureConfiguration mockFeatureConfiguration;
  @MockBean private FeatureService mockFeatureService;
  @MockBean private SamService mockSamService;
  @MockBean private TpsApiDispatch mockTpsApiDispatch;

  public CrlService mockCrlService() {
    return mockCrlService;
  }

  public FeatureConfiguration mockFeatureConfiguration() {
    return mockFeatureConfiguration;
  }

  public FeatureService mockFeatureService() {
    return mockFeatureService;
  }

  public SamService mockSamService() {
    return mockSamService;
  }

  public TpsApiDispatch mockTpsApiDispatch() {
    return mockTpsApiDispatch;
  }
}
