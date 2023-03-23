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
 * requires following parameters to be presented in the script's configuration: subscriptionId,
 * resourceGroupId, tenantId
 */
public abstract class ControlledAzureTestScriptBase extends WorkspaceAllocateTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(ControlledAzureTestScriptBase.class);

  protected ControlledAzureResourceApi azureApi;
  // resource suffix to easily locate resources in the resource group in case of troubleshooting
  protected String suffix;

  private String subscriptionId;
  private String resourceGroupId;
  private String tenantId;

  protected String getSubscriptionId() {
    return subscriptionId;
  }

  protected String getResourceGroupId() {
    return resourceGroupId;
  }

  protected String getTenantId() {
    return tenantId;
  }

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // create workspace
    super.doSetup(testUsers, workspaceApi);

    suffix = UUID.randomUUID().toString();
    logger.info("Azure resource suffix {}", suffix);

    // create Azure Cloud Context
    logger.info(
        "Azure cloud context params subscriptionId={}, resourceGroupId={}, tenantId={}",
        getSubscriptionId(),
        getResourceGroupId(),
        getTenantId());
    CloudContextMaker.createAzureCloudContext(
        getWorkspaceId(),
        workspaceApi,
        new AzureContext()
            .subscriptionId(getSubscriptionId())
            .resourceGroupId(getResourceGroupId())
            .tenantId(getTenantId()));
    logger.info("Created Azure cloud context in workspace {}", getWorkspaceId());
  }

  @Override
  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    subscriptionId = ParameterUtils.getParamOrThrow(parametersMap, "subscriptionId");
    resourceGroupId = ParameterUtils.getParamOrThrow(parametersMap, "resourceGroupId");
    tenantId = ParameterUtils.getParamOrThrow(parametersMap, "tenantId");
  }
}
