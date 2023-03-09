package bio.terra.workspace.service.resource.model;

/**
 * The resource state rule is used as a flight parameter to tell a flight how to handle resource
 * state. The
 */
public enum WsmResourceStateRule {
  /** When undoing a failed resource create, we delete the resource. */
  DELETE_ON_FAILURE,

  /** When undoing a failed resource create, we leave the resource in the broken state. */
  BROKEN_ON_FAILURE,
}
