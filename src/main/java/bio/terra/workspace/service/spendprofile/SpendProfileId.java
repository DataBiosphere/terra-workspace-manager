package bio.terra.workspace.service.spendprofile;

import com.google.auto.value.AutoValue;
import java.util.UUID;

/**
 * The unique id for a Spend Profile.
 *
 * <p>Implemented as a class wrapping a {@link UUID} so that this is strongly typed.
 */
@AutoValue
public abstract class SpendProfileId {
  public abstract UUID uuid();

  public static SpendProfileId create(UUID id) {
    return new AutoValue_SpendProfileId(id);
  }
}
