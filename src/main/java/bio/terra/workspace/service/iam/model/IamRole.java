package bio.terra.workspace.service.iam.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.EnumBiMap;
import com.google.common.collect.EnumHashBiMap;

/** Internal representation of IAM roles. */
public enum IamRole {
  READER,
  WRITER,
  OWNER;

  private static final BiMap<IamRole, String> samMap = EnumHashBiMap.create(IamRole.class);

  static {
    samMap.put(IamRole.READER, "reader");
    samMap.put(IamRole.WRITER, "writer");
    samMap.put(IamRole.OWNER, "owner");
  }

  public static IamRole fromSam(String samRole) {
    return samMap.inverse().get(samRole);
  }

  public String toSamRole() {
    return samMap.get(this);
  }
}
