package bio.terra.workspace.service.azurecompute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.service.azurecompute.exceptions.DeploymentFailedException;
import bio.terra.workspace.service.azurecompute.model.CreateDeploymentRequest;
import com.azure.resourcemanager.resources.fluentcore.model.Accepted;
import com.azure.resourcemanager.resources.models.Deployment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Disabled
public class AzureComputeServiceTest extends BaseConnectedTest {
  private final Logger logger = LoggerFactory.getLogger(AzureComputeServiceTest.class);

  @Autowired private AzureComputeService azureComputeService;
  String template;
  private CreateDeploymentRequest testCreateRequest;
  String deploymentName;

  @BeforeEach
  public void setup() {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream("azuredeploy.json")) {
      template = IOUtils.toString(stream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Problem reading resource", e);
    }

    deploymentName = "test" + UUID.randomUUID();

    testCreateRequest = new CreateDeploymentRequest(template, deploymentName);
  }

  @Test
  public void testDeployTemplate() throws IOException, DeploymentFailedException {
    Accepted<Deployment> deploy = azureComputeService.create(testCreateRequest);

    Deployment result = azureComputeService.pollUntilSuccess(deploy);

    assertEquals(result.provisioningState(), "Succeeded");
  }
}
