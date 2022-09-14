package bio.terra.workspace.app.controller.shared;

import bio.terra.workspace.common.utils.ControllerValidationUtils;
import bio.terra.workspace.generated.model.ApiProperty;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerUtils {

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
}
