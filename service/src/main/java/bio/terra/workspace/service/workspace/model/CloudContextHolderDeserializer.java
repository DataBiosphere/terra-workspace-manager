package bio.terra.workspace.service.workspace.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class CloudContextHolderDeserializer extends StdDeserializer<CloudContextHolder> {
  public CloudContextHolderDeserializer(Class<CloudContextHolder> t) {
    super(t);
  }

  @Override
  public CloudContextHolder deserialize(JsonParser jsonParser, DeserializationContext ctx)
      throws IOException {
    CloudContextHolder cch = new CloudContextHolder();
    JsonNode cchNode = jsonParser.readValueAsTree();

    JsonNode contextNode;
    if ((contextNode = cchNode.get("gcpCloudContext")) != null) {
      cch.setGcpCloudContext(GcpCloudContext.deserialize(contextNode.asText()));
    }
    if ((contextNode = cchNode.get("azureCloudContext")) != null) {
      cch.setAzureCloudContext(AzureCloudContext.deserialize(contextNode.asText()));
    }
    if ((contextNode = cchNode.get("awsCloudContext")) != null) {
      cch.setAwsCloudContext(AwsCloudContext.deserialize(contextNode.asText()));
    }

    return cch;
  }
}
