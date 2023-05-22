package bio.terra.workspace.service.policy;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PolicyValidator {
  private final TpsApiDispatch tpsApiDispatch;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AzureConfiguration azureConfiguration;
  private final ResourceDao resourceDao;
  private final WorkspaceDao workspaceDao;

  public PolicyValidator(
      TpsApiDispatch tpsApiDispatch,
      LandingZoneApiDispatch landingZoneApiDispatch,
      AzureConfiguration azureConfiguration,
      ResourceDao resourceDao,
      WorkspaceDao workspaceDao) {
    this.tpsApiDispatch = tpsApiDispatch;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.azureConfiguration = azureConfiguration;
    this.resourceDao = resourceDao;
    this.workspaceDao = workspaceDao;
  }

  /** throws PolicyConflictException if workspace does not conform to any policy */
  public void validateWorkspaceConformsToPolicy(
      Workspace workspace, TpsPaoGetResult policies, AuthenticatedUserRequest userRequest) {
    var validationErrors = new ArrayList<String>();

    if (policies == null) {
      return;
    }

    validationErrors.addAll(
        validateWorkspaceConformsToRegionPolicy(workspace, policies, userRequest));
    validationErrors.addAll(
        validateWorkspaceConformsToProtectedDataPolicy(workspace, policies, userRequest));
    validationErrors.addAll(
        validateWorkspaceConformsToGroupPolicy(workspace, policies, userRequest));

    if (!validationErrors.isEmpty()) {
      throw new PolicyConflictException(validationErrors);
    }
  }

  /** @return validation errors */
  public List<String> validateWorkspaceConformsToRegionPolicy(
      Workspace workspace, TpsPaoGetResult policies, AuthenticatedUserRequest userRequest) {
    var validationErrors = new ArrayList<String>();
    for (var cloudPlatform : workspaceDao.listCloudPlatforms(workspace.workspaceId())) {
      var validRegions = tpsApiDispatch.listValidRegionsForPao(policies, cloudPlatform);
      validationErrors.addAll(
          ResourceValidationUtils.validateExistingResourceRegions(
              workspace.workspaceId(), validRegions, cloudPlatform, resourceDao));

      if (cloudPlatform.equals(CloudPlatform.AZURE)) {
        // the landing zone in azure is region specific
        var lzRegion = landingZoneApiDispatch.getLandingZoneRegion(userRequest, workspace);
        if (validRegions.stream().noneMatch(r -> r.equalsIgnoreCase(lzRegion))) {
          validationErrors.add(
              "Workspace landing zone region %s is not one of %s"
                  .formatted(lzRegion, validRegions));
        }
      }
    }
    return validationErrors;
  }

  /** @return validation errors */
  public List<String> validateWorkspaceConformsToProtectedDataPolicy(
      Workspace workspace, TpsPaoGetResult policies, AuthenticatedUserRequest userRequest) {
    var validationErrors = new ArrayList<String>();
    if (TpsUtilities.containsProtectedDataPolicy(policies.getEffectiveAttributes())) {
      for (var cloudPlatform : workspaceDao.listCloudPlatforms(workspace.workspaceId())) {
        switch (cloudPlatform) {
          case AZURE -> {
            var lzDefinition =
                landingZoneApiDispatch.getLandingZone(userRequest, workspace).getDefinition();
            if (!azureConfiguration.getProtectedDataLandingZoneDefs().contains(lzDefinition)) {
              validationErrors.add(
                  "Workspace landing zone type [%s] does not support protected data"
                      .formatted(lzDefinition));
            }
          }

          default -> validationErrors.add("Protected data policy only supported on Azure");
        }
      }
    }
    return validationErrors;
  }

  public List<String> validateWorkspaceConformsToGroupPolicy(
      Workspace workspace, TpsPaoGetResult policies, AuthenticatedUserRequest userRequest) {
    var groups = TpsUtilities.getGroupConstraintsFromInputs(policies.getEffectiveAttributes());
    var currentPao = tpsApiDispatch.getPao((workspace.getWorkspaceId()));
    var existingGroups = TpsUtilities.getGroupConstraintsFromInputs(currentPao.getEffectiveAttributes());

    if (!groups.isEmpty()) {
      if (groups.containsAll(existingGroups) && existingGroups.containsAll(groups)) {
        // groups have not changed.
        return List.of();
      }
      return List.of("policies with group constraints not yet supported for this api call");
    } else {
      return List.of();
    }
  }
}
