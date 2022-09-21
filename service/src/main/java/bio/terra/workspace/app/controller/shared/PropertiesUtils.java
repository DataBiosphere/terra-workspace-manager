package bio.terra.workspace.app.controller.shared;

import static bio.terra.workspace.service.workspace.model.WorkspaceConstants.ResourceProperties.FOLDER_ID_KEY;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.model.ApiProperties;
import bio.terra.workspace.generated.model.ApiProperty;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utils for properties that are key value pairs in WSM workspace and resources. */
public class PropertiesUtils {

  // Convert properties list into a map
  public static ImmutableMap<String, String> convertApiPropertyToMap(List<ApiProperty> properties) {
    Map<String, String> propertyMap = new HashMap<>();
    if (properties != null) {
      for (ApiProperty property : properties) {
        ControllerValidationUtils.validatePropertyKey(property.getKey());
        propertyMap.put(property.getKey(), property.getValue());
      }
    }
    return ImmutableMap.copyOf(propertyMap);
  }

  public static ApiProperties convertMapToApiProperties(Map<String, String> properties) {
    var apiProperties = new ApiProperties();
    properties.forEach((key, value) -> apiProperties.add(new ApiProperty().key(key).value(value)));
    return apiProperties;
  }

  /**
   * Clear certain properties in the hashmap before making a clone of a resource.
   *
   * <p>For example, TERRA_WORKSPACE_FOLDER_ID is a workspace specific properties. It needs to be
   * cleared because it is meaningless in a new workspace.
   */
  public static ImmutableMap<String, String> clearSomePropertiesForResourceCloning(
      Map<String, String> properties) {
    HashMap<String, String> result = new HashMap<>(properties);
    result.remove(FOLDER_ID_KEY);
    return ImmutableMap.copyOf(result);
  }
}
