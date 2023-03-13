package scripts.disruptivescripts;

import bio.terra.testrunner.common.utils.KubernetesClientUtils;
import bio.terra.testrunner.runner.DisruptiveScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import io.kubernetes.client.openapi.models.V1Deployment;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteInitialPods extends DisruptiveScript {
  private static final Logger logger = LoggerFactory.getLogger(DeleteInitialPods.class);
  private static final long TIME_TO_WAIT = 3;

  public DeleteInitialPods() {
    super();
    manipulatesKubernetes = true;
  }

  protected static final int SECONDS_TO_WAIT_BEFORE_STARTING_DISRUPT = 15;

  @Override
  public void disrupt(List<TestUserSpecification> testUsers) throws Exception {
    // give user journey threads time to get started before disruption
    TimeUnit.SECONDS.sleep(SECONDS_TO_WAIT_BEFORE_STARTING_DISRUPT);

    logger.info(
        "Starting disruption - all initially created api pods will be deleted, one by one.");

    String componentLabelKey =
        KubernetesClientUtils.getComponentLabel(); // e.g. "app.kubernetes.io/component";
    String componentLabelVal =
        KubernetesClientUtils.getApiComponentLabel(); // e.g. "workspacemanager";

    V1Deployment workspacemanagerDeployment = KubernetesClientUtils.getApiDeployment();
    if (workspacemanagerDeployment == null) {
      throw new RuntimeException("WorkspaceManager deployment not found.");
    }

    // TODO (QA-1421): refactor this out into a method of the Test Runner KubernetesClientUtils
    // class.
    List<String> podsToDelete =
            KubernetesClientUtils.listPods().stream()
                    .filter(
                            pod ->
                                    pod.getMetadata().getLabels().containsKey(componentLabelKey)
                                            && pod.getMetadata()
                                            .getLabels()
                                            .get(componentLabelKey)
                                            .equals(componentLabelVal))
                    .map(pod -> pod.getMetadata().getName()).toList();

    // delete original pods, and give them a chance to recover
    for (String podName : podsToDelete) {
      logger.debug("delete pod: {}", podName);
      workspacemanagerDeployment = KubernetesClientUtils.getApiDeployment();
      if (workspacemanagerDeployment != null) {
        KubernetesClientUtils.printApiPods(workspacemanagerDeployment);
        KubernetesClientUtils.deletePod(podName);
        Calendar startWaiting = Calendar.getInstance();
        logger.debug(
            "start waiting for pod to recover at time {}",
            LocalDateTime.ofInstant(
                startWaiting.toInstant(), startWaiting.getTimeZone().toZoneId()));
        TimeUnit.SECONDS.sleep(TIME_TO_WAIT);
        KubernetesClientUtils.waitForReplicaSetSizeChange(
            workspacemanagerDeployment, podsToDelete.size());
        Calendar endWaiting = Calendar.getInstance();
        logger.debug(
            "end waiting for pod to recover at time {}",
            LocalDateTime.ofInstant(endWaiting.toInstant(), endWaiting.getTimeZone().toZoneId()));
        logger.debug(
            "pod recovers in {} ms", endWaiting.getTimeInMillis() - startWaiting.getTimeInMillis());
      }
    }

    logger.debug("original pods:");
    podsToDelete.forEach(logger::debug);
    KubernetesClientUtils.printApiPods(workspacemanagerDeployment);
  }
}
