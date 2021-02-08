package bio.terra.workspace.service.workspace.flight;

import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;

/**
 * Keys into the flight parameter map along with their types. Corresponds to
 * GoogleBucketCreationParameters
 */
public enum GoogleBucketFlightMapKeys {
  NAME("name", String.class),
  LOCATION("location", String.class),
  DEFAULT_STORAGE_CLASS("defaultStorageClass", GoogleBucketDefaultStorageClass.class),
  LIFECYCLE("lifecycle", GoogleBucketLifecycle.class);

  private final String key;
  private final Class<?> klass;

  GoogleBucketFlightMapKeys(String key, Class<?> klass) {
    this.key = key;
    this.klass = klass;
  }

  public String getKey() {
    return key;
  }

  public Class<? extends Object> getKlass() {
    return klass;
  }
}
