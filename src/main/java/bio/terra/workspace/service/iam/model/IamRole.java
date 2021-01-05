package bio.terra.workspace.service.iam.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.EnumHashBiMap;

/** Internal representation of IAM roles. */
public enum IamRole {
  READER,
  WRITER,
  OWNER;

  private static final BiMap<IamRole, String> samMap = EnumHashBiMap.create(IamRole.class);
  private static final BiMap<IamRole, bio.terra.workspace.generated.model.IamRole> apiMap =
      EnumBiMap.create(IamRole.class, bio.terra.workspace.generated.model.IamRole.class);

  static {
    samMap.put(IamRole.READER, "reader");
    samMap.put(IamRole.WRITER, "writer");
    samMap.put(IamRole.OWNER, "owner");

    apiMap.put(READER, bio.terra.workspace.generated.model.IamRole.READER);
    apiMap.put(WRITER, bio.terra.workspace.generated.model.IamRole.WRITER);
    apiMap.put(OWNER, bio.terra.workspace.generated.model.IamRole.OWNER);
  }

  public static IamRole fromApiModel(bio.terra.workspace.generated.model.IamRole apiModel) {
    return apiMap.inverse().get(apiModel);
  }

  public static IamRole fromSam(String samRole) {
    return samMap.inverse().get(samRole);
  }

  public bio.terra.workspace.generated.model.IamRole toApiModel() {
    return apiMap.get(this);
  }

  public String toSamRole() {
    return samMap.get(this);
  }
}
