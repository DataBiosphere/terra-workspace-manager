package scripts.utils;

import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.ReferenceResourceCommonFields;
import com.google.common.collect.ImmutableMap;

/** Utils to build common resource fields for both referenced resources and controlled resources. */
public class CommonResourceFieldsUtil {

  public static final ImmutableMap<String, String> DEFAULT_RESOURCE_PROPERTIES =
      ImmutableMap.of("foo", "bar");

  public static ReferenceResourceCommonFields makeReferencedResourceCommonFields(
      String name, CloningInstructionsEnum cloningInstructions) {
    return new ReferenceResourceCommonFields()
        .cloningInstructions(cloningInstructions)
        .description("Description of " + name)
        .name(name)
        .properties(getResourceDefaultProperties());
  }

  public static ControlledResourceCommonFields makeControlledResourceCommonFields(
      String name,
      PrivateResourceUser privateUser,
      CloningInstructionsEnum cloningInstructions,
      ManagedBy managedBy,
      AccessScope accessScope) {
    return new ControlledResourceCommonFields()
        .accessScope(accessScope)
        .managedBy(managedBy)
        .cloningInstructions(cloningInstructions)
        .description("Description of " + name)
        .name(name)
        .privateResourceUser(privateUser)
        .properties(getResourceDefaultProperties());
  }

  public static Properties getResourceDefaultProperties() {
    Properties properties = new Properties();
    DEFAULT_RESOURCE_PROPERTIES.forEach(
        (key, value) -> properties.add(new Property().key(key).value(value)));
    return properties;
  }
}
