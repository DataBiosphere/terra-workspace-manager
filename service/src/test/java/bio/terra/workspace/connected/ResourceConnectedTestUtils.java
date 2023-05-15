package bio.terra.workspace.connected;

import static bio.terra.workspace.common.fixtures.ControlledGcpResourceFixtures.makeDefaultControlledGcsBucketBuilder;

import bio.terra.workspace.generated.model.ApiGcpGcsBucketCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Utilities for working with resources in connected tests. */
@Component
public class ResourceConnectedTestUtils {

  @Autowired ControlledResourceService controlledResourceService;

  public ControlledResource createControlledBucket(
      AuthenticatedUserRequest userRequest, UUID workspaceId) {
    ControlledResource bucketResource = makeDefaultControlledGcsBucketBuilder(workspaceId).build();
    return controlledResourceService.createControlledResourceSync(
        bucketResource,
        ControlledResourceIamRole.OWNER,
        userRequest,
        new ApiGcpGcsBucketCreationParameters());
  }
}
