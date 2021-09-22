package bio.terra.workspace.azure.model;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DiskRequest {
  public abstract String diskName();

  public abstract int sizeInGB();
  //
  //  public static DiskRequest.Builder builder() {
  //      return new DiskRequest.Builder();
  //  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract DiskRequest.Builder diskName(String diskName);

    public abstract DiskRequest.Builder sizeInGB(int sizeInGB);

    public abstract DiskRequest build();
  }
}
