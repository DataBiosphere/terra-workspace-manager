package bio.terra.workspace.service.resource.controlled.cloud.aws;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.service.features.FeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

// Test to make sure things properly do not work when Azure feature is not enabled
// We are modifying application context here. Need to clean up once tests are done.
@Disabled("Until we get the postgres connection leaks addressed")
@Tag("connected")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AwsDisabledTest extends BaseConnectedTest {
  @MockBean private FeatureService featureService;

  @BeforeEach
  public void setUp() {
    // explicitly disable AWS feature regardless of the configuration files
    Mockito.when(featureService.awsEnabled()).thenReturn(false);
  }

  @Test
  public void awsDisabledTest() {
    // TODO-Dex
  }
}
