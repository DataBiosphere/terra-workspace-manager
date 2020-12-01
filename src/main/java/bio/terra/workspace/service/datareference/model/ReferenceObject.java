package bio.terra.workspace.service.datareference.model;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An interface representing the subject of a data reference.
 *
 * <p>All referenced objects are read-only value objects described by a set of string key-value
 * pairs. The set of keys available are specific to each referenced object type, as are validation
 * rules. ReferenceObject implementations must be fully serializable by the output of getProperties.
 *
 * <p>In order to properly deserialize implementations of this class, the implementation classes
 * must be added to the list inside the {@Code JsonSubTypes} annotation below. If the implementation
 * is an AutoValue class, the {@Code value} property should point to the abstract class, and the
 * {@Code name} should refer to the generated AutoValue class name. This allows us to use the
 * annotation information written in the abstract class while actually working with generated
 * AutoValue objects.
 */
@JsonTypeInfo(use = NAME, include = PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SnapshotReference.class, name = "AutoValue_SnapshotReference")
})
public interface ReferenceObject {

  /**
   * This objectMapper is static to ReferenceObjects so changes to other object mappers will not
   * affect serialization of these objects.
   */
  ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Method for serializing a ReferenceObject to a json string.
   *
   * <p>This method is used for Stairway and database serialization.
   */
  String toJson();

  /**
   * Method for deserializing a ReferenceObject from a json string.
   *
   * <p>This method is used for Stairway and database deserialization. This makes use of type
   * information stored via the {@Code JsonTypeInfo} annotation, and parses that information using
   * the {@Code JsonSubTypes} annotation.
   */
  static ReferenceObject fromJson(String jsonString) {
    try {
      return objectMapper.readValue(jsonString, ReferenceObject.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unable to deserialize ReferenceObject from string " + jsonString);
    }
  }
}
