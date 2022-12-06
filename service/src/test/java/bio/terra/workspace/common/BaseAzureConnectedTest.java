package bio.terra.workspace.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import bio.terra.workspace.app.configuration.external.AzureTestConfiguration;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to Azure */
@Tag("azure")
@ActiveProfiles({"azure-test", "connected-test"})
public class BaseAzureConnectedTest extends BaseTest {
  @Autowired private AzureTestConfiguration azureTestConfiguration;
  @MockBean private SpendProfileService spendProfileService;

  @BeforeEach
  void mockSpendProfileServiceResponse() {
    when(spendProfileService.authorizeLinking(any(), anyBoolean(), any()))
        .thenReturn(
            new SpendProfile(
                new SpendProfileId(UUID.randomUUID().toString()),
                CloudPlatform.AZURE,
                null,
                UUID.fromString(azureTestConfiguration.getTenantId()),
                UUID.fromString(azureTestConfiguration.getSubscriptionId()),
                azureTestConfiguration.getManagedResourceGroupId()));
  }
}
