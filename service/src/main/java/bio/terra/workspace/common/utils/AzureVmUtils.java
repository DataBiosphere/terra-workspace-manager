package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.ApiAzureLandingZoneParameter;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionTag;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AzureVmUtils {
  private AzureVmUtils() {}

  public static HashMap<String, Object> settingsFrom(
      List<ApiAzureVmCustomScriptExtensionSetting> settingsList) {
    return nullSafeListToStream(settingsList)
        .flatMap(Stream::ofNullable)
        .collect(
            Collectors.toMap(
                ApiAzureVmCustomScriptExtensionSetting::getKey,
                ApiAzureVmCustomScriptExtensionSetting::getValue,
                (prev, next) -> prev,
                HashMap::new));
  }

  public static HashMap<String, String> tagsFrom(
      List<ApiAzureVmCustomScriptExtensionTag> tagsList) {
    return nullSafeListToStream(tagsList)
        .flatMap(Stream::ofNullable)
        .collect(
            Collectors.toMap(
                ApiAzureVmCustomScriptExtensionTag::getKey,
                ApiAzureVmCustomScriptExtensionTag::getValue,
                (prev, next) -> prev,
                HashMap::new));
  }

  public static HashMap<String, String> landingZoneFrom(
      List<ApiAzureLandingZoneParameter> parametersList) {
    return nullSafeListToStream(parametersList)
        .flatMap(Stream::ofNullable)
        .collect(
            Collectors.toMap(
                ApiAzureLandingZoneParameter::getKey,
                ApiAzureLandingZoneParameter::getValue,
                (prev, next) -> prev,
                HashMap::new));
  }

  public static String getImageData(ApiAzureVmImage apiAzureVmImage) {
    return apiAzureVmImage.getUri() != null
        ? apiAzureVmImage.getUri()
        : String.format(
            "publisher=%s:offer=%s:sku=%s:version=%s",
            apiAzureVmImage.getPublisher(),
            apiAzureVmImage.getOffer(),
            apiAzureVmImage.getSku(),
            apiAzureVmImage.getVersion());
  }

  private static <T> Stream<T> nullSafeListToStream(Collection<T> collection) {
    return Optional.ofNullable(collection).stream().flatMap(Collection::stream);
  }
}
