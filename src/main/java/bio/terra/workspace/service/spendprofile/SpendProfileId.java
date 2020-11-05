package bio.terra.workspace.service.spendprofile;

import com.google.auto.value.AutoValue;
import java.util.UUID;

/**
 * The unique id for a Spend Profile.
 *
 * <p>Implemented as a class wrapping a {@link UUID} so that this is strongly typed.
 */
@AutoValue
@JsonSerialize(as = SpendProfileId.class)
@JsonDeserialize(as = AutoValue_SpendProfileId.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class SpendProfileId {
  @JsonProperty("uuid")
  public abstract UUID uuid();

  @JsonCreator
  public static SpendProfileId create(UUID id) {
    return new AutoValue_SpendProfileId(id);
  }
}
