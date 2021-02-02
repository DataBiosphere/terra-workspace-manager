package bio.terra.workspace.service.spendprofile;

import com.google.auto.value.AutoValue;
import org.jetbrains.annotations.NotNull;

/**
 * The unique id for a {@link SpendProfile}.
 *
 * <p>Implemented as a class wrapping a String so that this is strongly typed.
 */
@AutoValue
public abstract class SpendProfileId {
  public abstract String id();

  public static @NotNull SpendProfileId create(String id) {
    return new AutoValue_SpendProfileId(id);
  }
}
