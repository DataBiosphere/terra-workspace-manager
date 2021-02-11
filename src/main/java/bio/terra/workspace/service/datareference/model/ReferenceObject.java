package bio.terra.workspace.service.datareference.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An interface representing the subject of a data reference.
 *
 * <p>All referenced objects are read-only value objects described by a set of string key-value
 * pairs. The set of keys available are specific to each referenced object type, as are validation
 * rules. ReferenceObject implementations must be fully serializable by the output of getProperties.
 *
 * <p>In order to properly deserialize implementations of this class, the implementation classes
 * must be added to the list inside the {@code JsonSubTypes} annotation below. The implementation
 * class should specify a name using the {@code JsonTypeName} annotation so that changes to the
 * class name do not break backwards compatibility.
 */
@JsonTypeInfo(use = Id.NAME)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SnapshotReference.class, name = "SnapshotReference"),
  @JsonSubTypes.Type(value = GoogleBucketReference.class, name = "GoogleBucketReference"),
  @JsonSubTypes.Type(value = BigQueryDatasetReference.class, name = "BigQueryDatasetReference")
})
public interface ReferenceObject {

  /**
   * This objectMapper is static to ReferenceObjects so changes to other object mappers will not
   * affect serialization of these objects.
   */
  ObjectMapper objectMapper = new ObjectMapper();

  Logger logger = LoggerFactory.getLogger(ReferenceObject.class);

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
   * information stored via the {@code JsonTypeInfo} annotation, and parses that information using
   * the {@code JsonSubTypes} annotation.
   */
  static ReferenceObject fromJson(String jsonString) {
    try {
      return objectMapper.readValue(jsonString, ReferenceObject.class);
    } catch (JsonProcessingException e) {
      logger.error("Unable to deserialize ReferenceObject from string " + jsonString);
      throw new RuntimeException(
          "Unable to deserialize ReferenceObject from string. See logs for details.");
    }
  }
}
