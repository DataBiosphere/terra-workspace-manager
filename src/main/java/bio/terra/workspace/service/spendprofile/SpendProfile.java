package bio.terra.workspace.service.spendprofile;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * A Terra resource modeling an account for spending money in the cloud.
 *
 * <p>Each Spend Profile may have cloud native billing resources associated with it. Spend Profiles
 * also have Terra IAM to manage who is allowed to modify and link them.
 */
@AutoValue
public abstract class SpendProfile {
  /** The unique identifier of the SpendProfile. */
  public abstract SpendProfileId id();

  /** The id of the Google Billing Account associated with the SpendProfile, if there is one. */
  public abstract Optional<String> billingAccountId();

  public static @NotNull Builder builder() {
    return new AutoValue_SpendProfile.Builder();
  }

  /** A builder for {@link SpendProfile}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(SpendProfileId id);

    public abstract Builder billingAccountId(@Nullable String billingAccountId);

    public abstract @NotNull SpendProfile build();
  }
}
