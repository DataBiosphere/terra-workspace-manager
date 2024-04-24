package bio.terra.workspace.app.controller.shared;

import static bio.terra.workspace.app.controller.shared.PropertiesUtils.convertMapToApiProperties;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceStageModel;
import bio.terra.workspace.generated.model.ApiWsmPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.TpsApiConversionUtils;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceDescription;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceApiUtils {
  private final AwsCloudContextService awsCloudContextService;
  private final AzureCloudContextService azureCloudContextService;
  private final FeatureConfiguration features;
  private final GcpCloudContextService gcpCloudContextService;
  private final SpendProfileService spendProfileService;
  private final TpsApiDispatch tpsApiDispatch;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  @Autowired
  public WorkspaceApiUtils(
      AwsCloudContextService awsCloudContextService,
      AzureCloudContextService azureCloudContextService,
      FeatureConfiguration features,
      GcpCloudContextService gcpCloudContextService,
      SpendProfileService spendProfileService,
      TpsApiDispatch tpsApiDispatch,
      WorkspaceActivityLogService workspaceActivityLogService) {
    this.awsCloudContextService = awsCloudContextService;
    this.azureCloudContextService = azureCloudContextService;
    this.features = features;
    this.gcpCloudContextService = gcpCloudContextService;
    this.spendProfileService = spendProfileService;
    this.tpsApiDispatch = tpsApiDispatch;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  /**
   * Default workspace stage to RAWLS and convert to the internal enum
   *
   * @param apiWorkspaceStage nullable workspace stage from request body
   * @return valid workspace stage
   */
  public static WorkspaceStage getStageFromApiStage(ApiWorkspaceStageModel apiWorkspaceStage) {
    // Existing client libraries should not need to know about the stage, as they won't use any of
    // the features it gates. If stage isn't specified in a create request, we default to
    // RAWLS_WORKSPACE.
    return (apiWorkspaceStage == null
        ? WorkspaceStage.RAWLS_WORKSPACE
        : WorkspaceStage.fromApiModel(apiWorkspaceStage));
  }

  /**
   * If spend profile is provided, ensure the user has permission to use it and return a
   * SpendProfileId.
   *
   * @param userRequest user making the request
   * @param apiSpendProfile spend profile from the request body
   * @return nullable spend profile
   */
  public @Nullable SpendProfile validateSpendProfilePermission(
      AuthenticatedUserRequest userRequest, String apiSpendProfile) {
    if (apiSpendProfile == null) {
      return null;
    }
    return spendProfileService.authorizeLinking(new SpendProfileId(apiSpendProfile), userRequest);
  }

  /**
   * If policy inputs are provided, ensure this workspace is allowed to have the policies set. If
   * so, convert the policies from API to internal form.
   *
   * @param policyInputs API policies inputs
   * @return converted policy inputs or null
   */
  public @Nullable TpsPolicyInputs validateAndConvertPolicies(ApiWsmPolicyInputs policyInputs) {
    if (policyInputs == null) {
      return null;
    }
    if (!features.isTpsEnabled()) {
      throw new FeatureNotSupportedException(
          "TPS is not enabled on this instance of Workspace Manager, do not specify the policy field of a CreateWorkspace request.");
    }
    return TpsApiConversionUtils.tpsFromApiTpsPolicyInputs(policyInputs);
  }

  public ApiWorkspaceDescription buildApiWorkspaceDescription(
      WorkspaceDescription workspaceDescriptions) {
    Workspace workspace = workspaceDescriptions.workspace();
    if (workspaceDescriptions.highestRole() == WsmIamRole.DISCOVERER) {
      workspace = Workspace.stripWorkspaceForRequesterWithOnlyDiscovererRole(workspace);
    }

    // Convert the property map to API format
    ApiProperties apiProperties = convertMapToApiProperties(workspace.getProperties());

    return new ApiWorkspaceDescription()
        .id(workspace.workspaceId())
        .userFacingId(workspace.getUserFacingId())
        .displayName(workspace.getDisplayName().orElse(null))
        .description(workspace.getDescription().orElse(null))
        .highestRole(workspaceDescriptions.highestRole().toApiModel())
        .properties(apiProperties)
        .spendProfile(workspace.getSpendProfileId().map(SpendProfileId::getId).orElse(null))
        .stage(workspace.getWorkspaceStage().toApiModel())
        .gcpContext(
            Optional.ofNullable(workspaceDescriptions.gcpCloudContext())
                .map(GcpCloudContext::toApi)
                .orElse(null))
        .azureContext(
            Optional.ofNullable(workspaceDescriptions.azureCloudContext())
                .map(AzureCloudContext::toApi)
                .orElse(null))
        .awsContext(
            Optional.ofNullable(workspaceDescriptions.awsCloudContext())
                .map(AwsCloudContext::toApi)
                .orElse(null))
        .createdDate(workspace.createdDate())
        .createdBy(workspace.createdByEmail())
        .lastUpdatedDate(
            Optional.ofNullable(workspaceDescriptions.lastUpdatedByDate())
                .orElse(workspace.createdDate()))
        .lastUpdatedBy(
            Optional.ofNullable(workspaceDescriptions.lastUpdatedByEmail())
                .orElse(workspace.createdByEmail()))
        .policies(
            TpsApiConversionUtils.apiEffectivePolicyListFromTpsPao(
                workspaceDescriptions.workspacePolicies()))
        .missingAuthDomains(workspaceDescriptions.missingAuthDomains())
        .operationState(
            ControllerUtils.toApiOperationState(
                workspace.flightId(), workspace.state(), workspace.error()));
  }
}
