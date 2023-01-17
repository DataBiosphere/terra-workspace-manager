package scripts.utils;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledAzureResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.AzureContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all Azure related integration tests. It reads configuration required for Azure
 * cloud context creation and creates Azure cloud context based on these parameters. Each Azure test
 * requires following parameter to be presented in the script's configuration: spend-profile-id.
 */
public abstract class ControlledAzureTestScriptBase extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ControlledAzureTestScriptBase.class);

  protected static final String REGION = "westcentralus";

  protected ControlledAzureResourceApi azureApi;
  // resource suffix to easily locate resources in the resource group in case of troubleshooting
  protected String suffix;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // create workspace
    super.doSetup(testUsers, workspaceApi);

    suffix = UUID.randomUUID().toString();
    logger.info("Azure resource suffix {}", suffix);

    // create Azure Cloud Context
    CloudContextMaker.createAzureCloudContext(
        getWorkspaceId(),
        workspaceApi);
    logger.info("Created Azure cloud context in workspace {}", getWorkspaceId());
  }
}
