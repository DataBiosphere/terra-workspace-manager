package bio.terra.workspace.service.resource.controlled.flight.newclone.workspace;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageClass;

import java.util.List;
import java.util.Objects;

/**
 * Mutable class for incrementally building and passing around GCS bucket
 * parameters. These are either going to be attributes or are set on
 * the cloud resource, but not retained in the WSM metadata
 */
public class ControlledGcsBucketParameters {
  private String bucketName; // Could use the attributes object here; can't change on update
  private String location;
  private StorageClass storageClass;
  private List<? extends BucketInfo.LifecycleRule> lifecycleRules;

  public String getBucketName() {
    return bucketName;
  }

  public ControlledGcsBucketParameters setBucketName(String bucketName) {
    this.bucketName = bucketName;
    return this;
  }

  public String getLocation() {
    return location;
  }

  public ControlledGcsBucketParameters setLocation(String location) {
    this.location = location;
    return this;
  }

  public StorageClass getStorageClass() {
    return storageClass;
  }

  public ControlledGcsBucketParameters setStorageClass(StorageClass storageClass) {
    this.storageClass = storageClass;
    return this;
  }

  public List<? extends BucketInfo.LifecycleRule> getLifecycleRules() {
    return lifecycleRules;
  }

  public ControlledGcsBucketParameters setLifecycleRules(List<? extends BucketInfo.LifecycleRule> lifecycleRules) {
    this.lifecycleRules = lifecycleRules;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (ControlledGcsBucketParameters) obj;
    return Objects.equals(this.bucketName, that.bucketName) &&
      Objects.equals(this.location, that.location) &&
      Objects.equals(this.storageClass, that.storageClass) &&
      Objects.equals(this.lifecycleRules, that.lifecycleRules);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bucketName, location, storageClass, lifecycleRules);
  }

  @Override
  public String toString() {
    return "CloneGcsBucketExtras[" +
      "bucketName=" + bucketName + ", " +
      "location=" + location + ", " +
      "storageClass=" + storageClass + ", " +
      "lifecycleRules=" + lifecycleRules + ']';
  }

}
