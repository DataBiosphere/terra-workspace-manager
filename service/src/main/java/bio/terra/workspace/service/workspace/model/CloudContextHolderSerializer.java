package bio.terra.workspace.service.workspace.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import java.io.IOException;

public class CloudContextHolderSerializer extends JsonSerializer<CloudContextHolder> {
  public CloudContextHolderSerializer() {}

  @Override
  public void serializeWithType(
      CloudContextHolder cch,
      JsonGenerator jsonGen,
      SerializerProvider provider,
      TypeSerializer typeSer)
      throws IOException {
    WritableTypeId typeId = typeSer.typeId(cch, JsonToken.START_OBJECT);
    typeSer.writeTypePrefix(jsonGen, typeId);
    writeFields(cch, jsonGen);
    typeId.wrapperWritten = !jsonGen.canWriteTypeId();
    typeSer.writeTypeSuffix(jsonGen, typeId);
  }

  @Override
  public void serialize(CloudContextHolder cch, JsonGenerator jsonGen, SerializerProvider provider)
      throws IOException {
    jsonGen.writeStartObject();
    writeFields(cch, jsonGen);
    jsonGen.writeEndObject();
  }

  private void writeFields(CloudContextHolder cch, JsonGenerator jsonGen) throws IOException {
    if (cch.getGcpCloudContext() != null) {
      jsonGen.writeStringField("gcpCloudContext", cch.getGcpCloudContext().serialize());
    }
    if (cch.getAzureCloudContext() != null) {
      jsonGen.writeStringField("azureCloudContext", cch.getAzureCloudContext().serialize());
    }
    if (cch.getAwsCloudContext() != null) {
      jsonGen.writeStringField("awsCloudContext", cch.getAwsCloudContext().serialize());
    }
  }
}
