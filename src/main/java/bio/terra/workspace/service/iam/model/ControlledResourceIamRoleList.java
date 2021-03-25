package bio.terra.workspace.service.iam.model;

import java.util.ArrayList;

/**
 * A named type for an ArrayList of ControlledResourceIamRole enums. Used for type safety when
 * serializing a list in Stairway.
 */
public class ControlledResourceIamRoleList extends ArrayList<ControlledResourceIamRole> {}
