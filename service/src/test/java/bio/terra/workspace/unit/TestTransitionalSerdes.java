package bio.terra.workspace.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource;
import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: PF-2512 Remove this test when transition is complete
@Tag("unit")
public class TestTransitionalSerdes {
  private static final Logger logger = LoggerFactory.getLogger(TestTransitionalSerdes.class);

  private static final String RESOURCE_NOTEBOOK_JSON =
      """
         [
             "bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource",
             {
                 "wsmResourceFields": [
                     "bio.terra.workspace.service.resource.model.WsmResourceFields",
                     {
                         "workspaceId": "2199cecd-b10d-418a-a2ed-0e58abcb13fe",
                         "lastUpdatedDate": null,
                         "lastUpdatedByEmail": null,
                         "createdDate": null,
                         "createdByEmail": "avery.stormreaver@test.firecloud.org",
                         "properties": [
                             "com.google.common.collect.SingletonImmutableBiMap",
                             {
                                 "foo": "bar"
                             }
                         ],
                         "resourceLineage": [
                             "java.util.ArrayList",
                             []
                         ],
                         "cloningInstructions": "COPY_DEFINITION",
                         "description": "A bucket that had beer in it, briefly. 🍻",
                         "name": "ainotebook49ac9d8da5ec4609bb90ea416e01ef07",
                         "resourceId": "b4655ade-636b-4ea3-a871-727af1e678fe"
                     }
                 ],
                 "stewardshipType": "CONTROLLED",
                 "category": "USER_PRIVATE",
                 "resourceType": "CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE",
                 "resourceFamily": "AI_NOTEBOOK_INSTANCE",
                 "uniquenessCheckAttributes": [
                     "bio.terra.workspace.db.model.UniquenessCheckAttributes",
                     {
                         "parameters": [
                             "java.util.ArrayList",
                             [
                                 [
                                     "org.apache.commons.lang3.tuple.ImmutablePair",
                                     {
                                         "instanceId": "instanceid28fa416abd9e47a3a1deec30fa4830d6"
                                     }
                                 ],
                                 [
                                     "org.apache.commons.lang3.tuple.ImmutablePair",
                                     {
                                         "location": "asia-east1"
                                     }
                                 ]
                             ]
                         ],
                         "uniquenessScope": "WORKSPACE"
                     }
                 ],
                 "region": "asia-east1",
                 "lastUpdatedDate": null,
                 "lastUpdatedByEmail": null,
                 "createdDate": null,
                 "createdByEmail": "avery.stormreaver@test.firecloud.org",
                 "properties": [
                     "com.google.common.collect.SingletonImmutableBiMap",
                     {
                         "foo": "bar"
                     }
                 ],
                 "resourceLineage": [
                     "java.util.ArrayList",
                     []
                 ],
                 "projectId": "terra-wsm-t-beady-date-415",
                 "location": "asia-east1",
                 "instanceId": "instanceid28fa416abd9e47a3a1deec30fa4830d6",
                 "application": null,
                 "managedBy": "MANAGED_BY_USER",
                 "accessScope": "ACCESS_SCOPE_PRIVATE",
                 "privateResourceState": "INITIALIZING",
                 "assignedUser": "avery.stormreaver@test.firecloud.org",
                 "cloningInstructions": "COPY_DEFINITION",
                 "description": "A bucket that had beer in it, briefly. 🍻",
                 "name": "ainotebook49ac9d8da5ec4609bb90ea416e01ef07",
                 "resourceId": "b4655ade-636b-4ea3-a871-727af1e678fe",
                 "workspaceId": "2199cecd-b10d-418a-a2ed-0e58abcb13fe"
             }
         ]
         """;

  private static final String WSM_RESOURCE_FIELDS =
      """
    ["bio.terra.workspace.service.resource.model.WsmResourceFields",
    {
      "workspaceId": "2199cecd-b10d-418a-a2ed-0e58abcb13fe",
      "lastUpdatedDate": null,
      "lastUpdatedByEmail": null,
      "createdDate": null,
      "createdByEmail": "avery.stormreaver@test.firecloud.org",
      "properties": [
      "com.google.common.collect.SingletonImmutableBiMap",
        {
          "foo": "bar"
                             }
                         ],
      "resourceLineage": [
      "java.util.ArrayList",
                             []
                         ],
      "cloningInstructions": "COPY_DEFINITION",
      "description": "A bucket that had beer in it, briefly. 🍻",
      "name": "ainotebook49ac9d8da5ec4609bb90ea416e01ef07",
      "resourceId": "b4655ade-636b-4ea3-a871-727af1e678fe"
    }]
    """;

  private static final String AI_NOTEBOOK_NEW_FORMAT =
      """
      [
          "bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.ControlledAiNotebookInstanceResource",
          {
              "wsmControlledResourceFields": {
                  "region": "us-central1",
                  "applicationId": null,
                  "managedBy": "MANAGED_BY_USER",
                  "accessScope": "ACCESS_SCOPE_PRIVATE",
                  "privateResourceState": "NOT_APPLICABLE",
                  "assignedUser": null
              },
              "wsmResourceFields": [
                  "bio.terra.workspace.service.resource.model.WsmResourceFields",
                  {
                      "workspaceId": "d7a7aa86-4a82-47b6-9c73-f416bb2eb7be",
                      "lastUpdatedDate": null,
                      "lastUpdatedByEmail": null,
                      "createdDate": null,
                      "createdByEmail": "dd@dd.com",
                      "properties": [
                          "com.google.common.collect.RegularImmutableMap",
                          {
                          }
                      ],
                      "resourceLineage": [
                          "java.util.ArrayList",
                          []
                      ],
                      "cloningInstructions": "COPY_NOTHING",
                      "description": "fakeNotebook",
                      "name": "fakenotebook",
                      "resourceId": "814b272d-be51-4734-8057-2d150073fc8e"
                  }
              ],
              "applicationId": null,
              "region": "us-central1",
              "lastUpdatedDate": null,
              "lastUpdatedByEmail": null,
              "createdDate": null,
              "createdByEmail": "dd@dd.com",
              "properties": [
                  "com.google.common.collect.RegularImmutableMap",
                  {
                  }
              ],
              "resourceLineage": [
                  "java.util.ArrayList",
                  []
              ],
              "projectId": "projectId",
              "location": "location",
              "instanceId": "instanceid",
              "managedBy": "MANAGED_BY_USER",
              "accessScope": "ACCESS_SCOPE_PRIVATE",
              "privateResourceState": "NOT_APPLICABLE",
              "assignedUser": null,
              "cloningInstructions": "COPY_NOTHING",
              "description": "fakeNotebook",
              "name": "fakenotebook",
              "resourceId": "814b272d-be51-4734-8057-2d150073fc8e",
              "workspaceId": "d7a7aa86-4a82-47b6-9c73-f416bb2eb7be"
          }
      ]
      """;

  @Test
  public void testOldNotebookSerdes() throws Exception {
    JobService.configureMapper();

    FlightMap testMap = new FlightMap();
    testMap.putRaw("foo", RESOURCE_NOTEBOOK_JSON);
    ControlledAiNotebookInstanceResource resource =
        testMap.get("foo", ControlledAiNotebookInstanceResource.class);

    testMap.put("bar", resource);
    String jsonString = testMap.getRaw("bar");
    logger.info("New JSON: {}", jsonString);

    ControlledAiNotebookInstanceResource moreResource =
        testMap.get("bar", ControlledAiNotebookInstanceResource.class);
    assertTrue(moreResource.partialEqual(resource));
  }

  @Test
  public void testNewNotebookSerdes() throws Exception {
    JobService.configureMapper();
    var fields =
        ControlledResourceFields.builder()
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .applicationId(null)
            .assignedUser(null)
            .iamRole(ControlledResourceIamRole.OWNER)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .region("us-central1")
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .createdByEmail("dd@dd.com")
            .description("fakeNotebook")
            .name("fakenotebook")
            .resourceId(UUID.randomUUID())
            .workspaceUuid(UUID.randomUUID())
            .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
            .build();

    var resource =
        new ControlledAiNotebookInstanceResource(fields, "instanceid", "location", "projectId");

    String jsonString = StairwayMapper.getObjectMapper().writeValueAsString(resource);
    logger.info("Ser: {}", jsonString);
    var moreResource =
        StairwayMapper.getObjectMapper()
            .readValue(jsonString, ControlledAiNotebookInstanceResource.class);
    logger.info("Des: {}", moreResource);
    String moreJsonString = StairwayMapper.getObjectMapper().writeValueAsString(moreResource);
    logger.info("MoreSer: {}", moreJsonString);
    assertTrue(moreResource.partialEqual(resource));
  }

  @Test
  public void testNewFormatNewCreator() throws Exception {
    JobService.configureMapper();
    var fields =
        ControlledResourceFields.builder()
            .accessScope(AccessScopeType.ACCESS_SCOPE_PRIVATE)
            .applicationId(null)
            .assignedUser(null)
            .iamRole(ControlledResourceIamRole.OWNER)
            .managedBy(ManagedByType.MANAGED_BY_USER)
            .region("us-central1")
            .cloningInstructions(CloningInstructions.COPY_NOTHING)
            .createdByEmail("dd@dd.com")
            .description("fakeNotebook")
            .name("fakenotebook")
            .resourceId(UUID.randomUUID())
            .workspaceUuid(UUID.randomUUID())
            .privateResourceState(PrivateResourceState.NOT_APPLICABLE)
            .build();

    var resource =
        StairwayMapper.getObjectMapper()
            .readValue(AI_NOTEBOOK_NEW_FORMAT, ControlledAiNotebookInstanceResource.class);
    String jsonString = StairwayMapper.getObjectMapper().writeValueAsString(resource);
    logger.info("Ser: {}", jsonString);
    var moreResource =
        StairwayMapper.getObjectMapper()
            .readValue(jsonString, ControlledAiNotebookInstanceResource.class);
    logger.info("Des: {}", moreResource);
    String moreJsonString = StairwayMapper.getObjectMapper().writeValueAsString(moreResource);
    logger.info("MoreSer: {}", moreJsonString);
    assertTrue(moreResource.partialEqual(resource));
  }
}
