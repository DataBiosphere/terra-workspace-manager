package scripts.utils;

import static scripts.utils.CommonResourceFieldsUtil.makeControlledResourceCommonFields;

import bio.terra.workspace.api.ControlledFlexibleResourceApi;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledFlexibleResourceCreationParameters;
import bio.terra.workspace.model.CreateControlledFlexibleResourceRequestBody;
import bio.terra.workspace.model.FlexibleResource;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceUser;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlexResourceUtils {

  private static final Logger logger = LoggerFactory.getLogger(FlexResourceUtils.class);

  public static FlexibleResource makeFlexibleResourceShared(
      ControlledFlexibleResourceApi resourceApi,
      UUID workspaceUuid,
      String resourceName,
      String flexType,
      String flexTypeNamespace,
      @Nullable byte[] data,
      @Nullable CloningInstructionsEnum cloningInstructions)
      throws Exception {
    return makeFlexibleResource(
        resourceApi,
        workspaceUuid,
        resourceName,
        flexType,
        flexTypeNamespace,
        data,
        AccessScope.SHARED_ACCESS,
        ManagedBy.USER,
        cloningInstructions,
        null);
  }

  // Decode the base64, so we can store the string directly in the database.
  public static String getDecodedJSONFromByteArray(@Nullable byte[] encodedJSON) {
    if (encodedJSON == null) {
      return null;
    }
    return new String(encodedJSON, StandardCharsets.UTF_8);
  }

  // Encode the string in base64, so we can pass it through the API.
  public static byte[] getEncodedJSONFromString(@Nullable String decodedJSON) {
    if (decodedJSON == null) {
      return null;
    }
    return decodedJSON.getBytes(StandardCharsets.UTF_8);
  }

  /** Create and return a controlled Flexible resource. */
  private static FlexibleResource makeFlexibleResource(
      ControlledFlexibleResourceApi resourceApi,
      UUID workspaceUuid,
      String resourceName,
      String flexType,
      String flexTypeNamespace,
      @Nullable byte[] flexData,
      AccessScope accessScope,
      ManagedBy managedBy,
      @Nullable CloningInstructionsEnum cloningInstructions,
      @Nullable PrivateResourceUser privateUser)
      throws Exception {

    var body =
        new CreateControlledFlexibleResourceRequestBody()
            .common(
                makeControlledResourceCommonFields(
                    resourceName,
                    privateUser,
                    Optional.ofNullable(cloningInstructions)
                        .orElse(CloningInstructionsEnum.NOTHING),
                    managedBy,
                    accessScope))
            .flexibleResource(
                new ControlledFlexibleResourceCreationParameters()
                    .typeNamespace(flexTypeNamespace)
                    .type(flexType)
                    .data(flexData));

    logger.info(
        "Creating {} {} Flex resource (name: {}) in workspace {}",
        managedBy.name(),
        accessScope.name(),
        resourceName,
        workspaceUuid);
    FlexibleResource result =
        resourceApi.createFlexibleResource(body, workspaceUuid).getFlexibleResource();
    logger.info(
        "Created {} {} Flex resource (name: {}) in workspace {}",
        managedBy.name(),
        accessScope.name(),
        resourceName,
        workspaceUuid);
    return result;
  }
}
