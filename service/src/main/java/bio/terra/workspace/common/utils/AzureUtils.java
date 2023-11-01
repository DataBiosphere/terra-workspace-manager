package bio.terra.workspace.common.utils;

import bio.terra.common.exception.ValidationException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionSetting;
import bio.terra.workspace.generated.model.ApiAzureVmCustomScriptExtensionTag;
import bio.terra.workspace.generated.model.ApiAzureVmImage;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Various utilities for validating requests in Azure Controllers. */
public final class AzureUtils {

  /**
   * Validate that the expiration duration (in seconds) is between 1 and the maximum allowed
   * duration (in minutes).
   *
   * @param sasExpirationDuration user-specified duration in seconds (note that null is allowed)
   * @param maxDurationMinutes maximum allowed duration in minutes
   * @throws ValidationException if sasExpiration is not positive or is greater than maximum allowed
   *     duration. Does not throw an exception if sasExpiration is null.
   */
  public static void validateSasExpirationDuration(
      @Nullable Long sasExpirationDuration, Long maxDurationMinutes) {
    if (sasExpirationDuration == null) {
      return;
    }
    if (sasExpirationDuration <= 0) {
      throw new ValidationException(
          "sasExpirationDuration must be positive: " + sasExpirationDuration);
    }
    long maxDurationSeconds = 60 * maxDurationMinutes;
    if (sasExpirationDuration > maxDurationSeconds) {
      throw new ValidationException(
          String.format(
              "sasExpirationDuration cannot be greater than allowed maximum (%d): %d",
              maxDurationSeconds, sasExpirationDuration));
    }
  }

  /**
   * Validate an azure blob name. Blob name may be a string or null, and must be > 1 char and < 1024
   * chars in length.
   *
   * @param blobName Blob name to validate, or null.
   */
  public static void validateSasBlobName(@Nullable String blobName) {
    if (blobName == null) {
      return;
    }
    if (blobName.isEmpty()) {
      throw new ValidationException("Blob name may not be empty");
    }
    if (blobName.length() > 1024) {
      throw new ValidationException("Blob name must be <= 1024 chars");
    }
  }

  public static HashMap<String, Object> vmSettingsFrom(
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

  public static HashMap<String, String> vmTagsFrom(
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

  public static String getVmImageData(ApiAzureVmImage apiAzureVmImage) {
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

  public static TokenCredential getManagedAppCredentials(AzureConfiguration azureConfig) {
    return new ClientSecretCredentialBuilder()
            .clientId(azureConfig.getManagedAppClientId())
            .clientSecret(azureConfig.getManagedAppClientSecret())
            .tenantId(azureConfig.getManagedAppTenantId())
            .build();
  }

  public static AzureProfile getAzureProfile(AzureCloudContext azureCloudContext) {
    return new AzureProfile(
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureSubscriptionId(),
            AzureEnvironment.AZURE);
  }
}
