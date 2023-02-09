package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import java.util.Optional;
import java.util.UUID;

public class MergePolicyAttributesUtils {
  // Since policy attributes were added later in development, not all existing
  // workspaces have an associated policy attribute object. This method creates
  // an empty one if it does not exist.
  protected static void createPaoIfNotExist(TpsApiDispatch tpsApiDispatch, UUID workspaceId) {
    Optional<TpsPaoGetResult> pao = tpsApiDispatch.getPaoIfExists(workspaceId);
    if (pao.isPresent()) {
      return;
    }
    // Workspace doesn't have a PAO, so create an empty one for it.
    tpsApiDispatch.createPao(workspaceId, null);
  }
}
