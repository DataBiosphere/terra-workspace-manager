package bio.terra.workspace.service.workspace.flight;

import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;

/**
 * Keys into the flight parameter map along with their types. Corresponds to
 * GoogleBucketCreationParameters
 */
public enum GoogleBucketFlightMapKeys implements FlightMapKey {
  NAME("name", String.class),
  LOCATION("location", String.class),
  DEFAULT_STORAGE_CLASS("defaultStorageClass", GoogleBucketDefaultStorageClass.class),
  LIFECYCLE("lifecycle", GoogleBucketLifecycle.class),
  BUCKET_CREATION_PARAMS("bucket_creation_params", GoogleBucketCreationParameters.class);

  private final String key;
  private final Class<?> klass;

  GoogleBucketFlightMapKeys(String key, Class<?> klass) {
    this.key = key;
    this.klass = klass;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public Class<?> getKlass() {
    return klass;
  }
}
