package bio.terra.workspace.azure.model;

// import bio.terra.workspace.service.workspace.model.AutoValue_WorkspaceRequest;
import com.azure.core.management.Region;
import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Internal representation of a request to create an azure VM. */
@AutoValue
public abstract class VirtualMachineRequest {

  /** The unique identifiers of this vm */
  public abstract String resourceGroupName();

  public abstract Region region();

  public abstract String name();

  public abstract String ipName();

  public abstract Optional<DiskRequest> diskRequest();

  public abstract NetworkRequest networkRequest();

  //  public static VirtualMachineRequest.Builder builder() {
  //    return new AutoValue_VirtualMachineRequest.Builder();
  //  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract VirtualMachineRequest.Builder resourceGroupName(String resourceGroupName);

    public abstract VirtualMachineRequest.Builder region(Region region);

    public abstract VirtualMachineRequest.Builder name(String name);

    public abstract VirtualMachineRequest.Builder networkRequest(NetworkRequest networkRequest);

    public abstract VirtualMachineRequest.Builder diskRequest(Optional<DiskRequest> diskRequest);

    public abstract VirtualMachineRequest.Builder ipName(String ipName);

    public abstract VirtualMachineRequest build();
  }
}
