package bio.terra.workspace.azure.model;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class NetworkRequest {
  public abstract String networkName();

  public abstract String subnetName();

  public abstract String addressCidr();

  public abstract String subnetAddressCidr();

  public static NetworkRequest.Builder builder() {
    return new AutoValue_NetworkRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract NetworkRequest.Builder networkName(String networkName);

    public abstract NetworkRequest.Builder subnetName(String subnetName);

    public abstract NetworkRequest.Builder addressCidr(String addressCidr);

    public abstract NetworkRequest.Builder subnetAddressCidr(String subnetAddressCidr);

    public abstract NetworkRequest build();
  }
}
