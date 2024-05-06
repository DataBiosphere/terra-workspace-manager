package bio.terra.workspace.pact;

import java.util.Map;
import java.util.UUID;

public class PactFixtures {

// Note: the header must match exactly so pact doesn't add it's own
// if "Content-type" is specified instead,
// pact will also have a required header for "Content-Type: application/json; charset=UTF-8"
// which will cause the request to fail to match,
// since our client doesn't include the encoding in the content type header
  public static final Map<String, String> CONTENT_TYPE_JSON_HEADER =
      Map.of("Content-Type", "application/json");

  public static final UUID LANDING_ZONE_ID =
      UUID.fromString("a051c5f8-9de8-4b4f-a453-8f9645df9c7b");
  public static final UUID ASYNC_JOB_ID = UUID.fromString("3b0db775-1916-42d2-aaeb-ebc7aea69eca");
  public static final UUID BILLING_PROFILE_ID =
      UUID.fromString("4955757a-b027-4b6c-b77b-4cba899864cd");
}
