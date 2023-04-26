package bio.terra.workspace.service.policy.model;

import bio.terra.workspace.app.controller.shared.PropertiesUtils;
import bio.terra.workspace.generated.model.ApiWsmPolicyObject;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

public record PolicyObject(
    UUID objectId,
    PolicyObjectType objectType,
    PolicyComponent component,
    boolean deleted,
    boolean access,
    @Nullable String name,
    Map<String, String> properties,
    String createdDate,
    String lastUpdatedDate) {

  public ApiWsmPolicyObject toApi() {
    var object =
        new ApiWsmPolicyObject()
            .objectId(objectId)
            .objectType(objectType.toApi())
            .component(component.toApi())
            .access(access)
            .deleted(deleted)
            .createdDate(createdDate)
            .lastUpdatedDate(lastUpdatedDate);
    if (access) {
      object.name(name).properties(PropertiesUtils.convertMapToApiProperties(properties));
    }
    return object;
  }
}
