package bio.terra.workspace.service.iam.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;
import java.util.HashMap;
import java.util.Map;

/** Internal representation of IAM roles. */
public enum IamRole {
  READER,
  WRITER,
  OWNER;

  private static final Map<IamRole, String> samMap = new HashMap<>();
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

  public bio.terra.workspace.generated.model.IamRole toApiModel() {
    return apiMap.get(this);
  }

  public String toSamRole() {
    String role = samMap.get(this);
    return role;
  }
}
