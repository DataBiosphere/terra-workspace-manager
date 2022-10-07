package bio.terra.workspace.app.controller.shared;

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
   * Clear certain properties in the hashmap before making a clone of a resource to a different
   * workspace.
   */
  public static ImmutableMap<String, String>
      clearSomePropertiesForResourceCloningToDifferentWorkspace(Map<String, String> properties) {
    HashMap<String, String> result = new HashMap<>(properties);
    // Remove certain properties here if you need
    return ImmutableMap.copyOf(result);
  }
}
