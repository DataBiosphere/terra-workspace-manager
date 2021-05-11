package bio.terra.workspace.service.spendprofile;

import com.google.auto.value.AutoValue;

/**
 * The unique id for a {@link SpendProfile}.
 *
 * <p>Implemented as a class wrapping a String so that this is strongly typed.
 */
@AutoValue
public abstract class SpendProfileId {
  public abstract String id();

  public static SpendProfileId create(String id) {
    return new AutoValue_SpendProfileId(id);
  }
}
