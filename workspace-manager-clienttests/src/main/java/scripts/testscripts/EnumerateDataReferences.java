package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class EnumerateDataReferences extends WorkspaceAllocateTestScriptBase {

  private static final Logger logger = LoggerFactory.getLogger(EnumerateDataReferences.class);

  private static final int DATA_REFERENCE_COUNT = 10;
  private static final int PAGE_SIZE = 4;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {

    // initialize workspace
    super.doSetup(testUsers, workspaceApi);

    // static assumptions
    assertThat(PAGE_SIZE * 2, lessThan(DATA_REFERENCE_COUNT));
    assertThat(PAGE_SIZE * 3, greaterThan(DATA_REFERENCE_COUNT));

    // create 10 data references
    final List<CreateDataReferenceRequestBody> createBodies =
        IntStream.range(0, DATA_REFERENCE_COUNT)
            .mapToObj(i -> ClientTestUtils.getTestCreateDataReferenceRequestBody())
            .collect(Collectors.toList());

    logger.debug("Creating references for workspace {}", getWorkspaceId());
    final ImmutableList.Builder<UUID> referenceIdListBuilder = ImmutableList.builder();
    for (CreateDataReferenceRequestBody body : createBodies) {
      final DataReferenceDescription dataReferenceDescription =
          workspaceApi.createDataReference(body, getWorkspaceId());
      referenceIdListBuilder.add(dataReferenceDescription.getReferenceId());
      logger.debug(
          "Created Data Reference Name: {}, ID: {}",
          dataReferenceDescription.getName(),
          dataReferenceDescription.getReferenceId().toString());
    }

    List<UUID> dataReferenceIds = referenceIdListBuilder.build();
    assertThat(dataReferenceIds.size(), equalTo(DATA_REFERENCE_COUNT));

    logger.info("Created {} data references", dataReferenceIds.size());
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {

    // fetch all
    final List<DataReferenceDescription> referenceDescriptions =
        ClientTestUtils.getDataReferenceDescriptions(
            getWorkspaceId(), workspaceApi, 0, DATA_REFERENCE_COUNT);
    assertThat(referenceDescriptions.size(), equalTo(DATA_REFERENCE_COUNT));

    final String allIdString =
        referenceDescriptions.stream()
            .map(DataReferenceDescription::getReferenceId)
            .map(UUID::toString)
            .collect(Collectors.joining("\n\t"));
    logger.info("Retrieved offset 0, limit {}, IDs:\n\t{}", DATA_REFERENCE_COUNT, allIdString);

    // should have all unique ids
    final ImmutableSet<UUID> referenceIds =
        ImmutableSet.copyOf(
            referenceDescriptions.stream()
                .map(DataReferenceDescription::getReferenceId)
                .collect(Collectors.toSet()));
    assertThat(referenceIds.size(), equalTo(DATA_REFERENCE_COUNT));

    // fetch first page
    final List<DataReferenceDescription> referenceDescriptionsPage0 =
        ClientTestUtils.getDataReferenceDescriptions(getWorkspaceId(), workspaceApi, 0, PAGE_SIZE);
    assertThat(referenceDescriptionsPage0.size(), equalTo(PAGE_SIZE));
    final ImmutableSet<UUID> referenceIdsPage0 =
        ImmutableSet.copyOf(
            referenceDescriptionsPage0.stream()
                .map(DataReferenceDescription::getReferenceId)
                .collect(Collectors.toSet()));
    assertThat(referenceIdsPage0.size(), equalTo(PAGE_SIZE));

    // fetch second page
    final List<DataReferenceDescription> referenceDescriptionsPage1 =
        ClientTestUtils.getDataReferenceDescriptions(
            getWorkspaceId(), workspaceApi, PAGE_SIZE, PAGE_SIZE);
    assertThat(referenceDescriptionsPage1.size(), equalTo(PAGE_SIZE));
    final ImmutableSet<UUID> referenceIdsPage1 =
        ImmutableSet.copyOf(
            referenceDescriptionsPage1.stream()
                .map(DataReferenceDescription::getReferenceId)
                .collect(Collectors.toSet()));
    assertThat(referenceIdsPage1.size(), equalTo(PAGE_SIZE));

    // Assure no repeats
    final ImmutableSet.Builder<UUID> idsFromTwoPagesBuilder = ImmutableSet.builder();
    idsFromTwoPagesBuilder.addAll(referenceIdsPage0);
    idsFromTwoPagesBuilder.addAll(referenceIdsPage1);
    final var idsFromTwoPages = idsFromTwoPagesBuilder.build();
    assertThat(idsFromTwoPages.size(), equalTo(2 * PAGE_SIZE));

    // final partial page
    final List<DataReferenceDescription> referenceDescriptionsPage2 =
        ClientTestUtils.getDataReferenceDescriptions(
            getWorkspaceId(), workspaceApi, 2 * PAGE_SIZE, PAGE_SIZE);
    assertThat(referenceDescriptionsPage2.size(), equalTo(DATA_REFERENCE_COUNT - 2 * PAGE_SIZE));
    final ImmutableSet<UUID> referenceIdsPage2 =
        ImmutableSet.copyOf(
            referenceDescriptionsPage2.stream()
                .map(DataReferenceDescription::getReferenceId)
                .collect(Collectors.toSet()));
    assertThat(referenceIdsPage2.size(), equalTo(referenceDescriptionsPage2.size()));

    // Assure no repeats
    final ImmutableSet.Builder<UUID> allIdsBuilder = ImmutableSet.builder();
    allIdsBuilder.addAll(referenceIdsPage0);
    allIdsBuilder.addAll(referenceIdsPage1);
    allIdsBuilder.addAll(referenceIdsPage2);
    final var allIds = allIdsBuilder.build();
    assertThat(allIds.size(), equalTo(DATA_REFERENCE_COUNT));

    // Assure no results if offset is too high
    final List<DataReferenceDescription> referencesBeyondUpperBound =
        ClientTestUtils.getDataReferenceDescriptions(
            getWorkspaceId(), workspaceApi, 10 * PAGE_SIZE, PAGE_SIZE);
    assertThat(referencesBeyondUpperBound, is(empty()));
  }
}
