package bio.terra.workspace.db.model;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class is returned from resource handlers to the ResourceDao. It describes the uniqueness
 * check that the ResourceDao should do. At this time, only string compares are supported, since
 * those are all that are in use. We can make this more complex if we need other datatypes.
 */
public class UniquenessCheckParameters {
  /** The scope of the uniqueness check */
  public enum UniquenessScope {
    /** search all resources of this type in the WSM database */
    WSM,
    /** search resources of this type within the workspace */
    WORKSPACE;
  }

  private final UniquenessScope uniquenessScope;
  private final List<Pair<String, String>> parameters;

  public UniquenessCheckParameters(UniquenessScope uniquenessScope) {
    this.uniquenessScope = uniquenessScope;
    this.parameters = new ArrayList<>();
  }

  public UniquenessScope getUniquenessScope() {
    return uniquenessScope;
  }

  public List<Pair<String, String>> getParameters() {
    return parameters;
  }

  /**
   * Add a parameter to be filtered. The filters are JSONB references of the form:
   * attributes->>'name' = value
   *
   * @param name name of the attribute to check
   * @param value value of the attribute to check
   * @return this - for fluent style
   */
  public UniquenessCheckParameters addParameter(String name, String value) {
    parameters.add(Pair.of(name, value));
    return this;
  }
}
