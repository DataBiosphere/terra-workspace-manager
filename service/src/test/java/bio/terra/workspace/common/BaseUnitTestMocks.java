package bio.terra.workspace.common;

import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import org.springframework.boot.test.mock.mockito.MockBean;

/*
 * Mock beans used by (or not conflicting with) all unit tests
 */
public class BaseUnitTestMocks extends BaseTest {
  @MockBean private ControlledResourceMetadataManager mockControlledResourceMetadataManager;
  @MockBean private ControlledResourceService mockControlledResourceService;
  @MockBean private CrlService mockCrlService;
  @MockBean private FeatureConfiguration mockFeatureConfiguration;
  @MockBean private SamService mockSamService;
  @MockBean private TpsApiDispatch mockTpsApiDispatch;

  public ControlledResourceMetadataManager mockControlledResourceMetadataManager() {
    return mockControlledResourceMetadataManager;
  }

  public ControlledResourceService mockControlledResourceService() {
    return mockControlledResourceService;
  }

  public CrlService mockCrlService() {
    return mockCrlService;
  }

  public FeatureConfiguration mockFeatureConfiguration() {
    return mockFeatureConfiguration;
  }

  public SamService mockSamService() {
    return mockSamService;
  }

  public TpsApiDispatch mockTpsApiDispatch() {
    return mockTpsApiDispatch;
  }
}
