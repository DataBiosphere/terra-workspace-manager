package bio.terra.workspace.service.workspace.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class CloudContextHolderSerializer extends StdSerializer<CloudContextHolder> {
  public CloudContextHolderSerializer() {
    this(null);
  }

  public CloudContextHolderSerializer(Class<CloudContextHolder> t) {
    super(t);
  }

  @Override
  public void serialize(CloudContextHolder cch, JsonGenerator jsonGen, SerializerProvider provider)
      throws IOException {
    jsonGen.writeStartObject();
    if (cch.getGcpCloudContext() != null) {
      jsonGen.writeStringField("gcpCloudContext", cch.getGcpCloudContext().serialize());
    }
    if (cch.getAzureCloudContext() != null) {
      jsonGen.writeStringField("azureCloudContext", cch.getAzureCloudContext().serialize());
    }
    jsonGen.writeEndObject();
  }
}
