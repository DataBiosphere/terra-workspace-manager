package scripts.disruptivescripts;

import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.common.utils.ProcessUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import okhttp3.Call;
import okhttp3.Response;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class K8sClientUtils {
    private static final Logger logger = LoggerFactory.getLogger(K8sClientUtils.class);
    private static int maximumSecondsToWaitForReplicaSetSizeChange = 500;
    private static int secondsIntervalToPollReplicaSetSizeChange = 5;
    public static final String componentLabel = "app.kubernetes.io/component";
    public static final String apiComponentLabel = "api";
    private static String namespace;
    private static CoreV1Api kubernetesClientCoreObject;
    private static AppsV1Api kubernetesClientAppsObject;

    private K8sClientUtils() {
    }

    public static CoreV1Api getKubernetesClientCoreObject() {
        if (kubernetesClientCoreObject == null) {
            throw new UnsupportedOperationException("Kubernetes client core object is not setup. Check the server configuration skipKubernetes property.");
        } else {
            return kubernetesClientCoreObject;
        }
    }

    public static AppsV1Api getKubernetesClientAppsObject() {
        if (kubernetesClientAppsObject == null) {
            throw new UnsupportedOperationException("Kubernetes client apps object is not setup. Check the server configuration skipKubernetes property.");
        } else {
            return kubernetesClientAppsObject;
        }
    }

    public static void buildKubernetesClientObject(ServerSpecification server) throws Exception {
        logger.debug("Calling the fetchGKECredentials script that uses gcloud to generate the kubeconfig file");
        List<String> scriptArgs = new ArrayList();
        scriptArgs.add("tools/fetchGKECredentials.sh");
        scriptArgs.add(server.cluster.clusterShortName);
        scriptArgs.add(server.cluster.region);
        scriptArgs.add(server.cluster.project);
        Process fetchCredentialsProc = ProcessUtils.executeCommand("sh", scriptArgs);
        List<String> cmdOutputLines = ProcessUtils.waitForTerminateAndReadStdout(fetchCredentialsProc);
        Iterator var4 = cmdOutputLines.iterator();

        while(var4.hasNext()) {
            String cmdOutputLine = (String)var4.next();
            logger.debug(cmdOutputLine);
        }

        String kubeConfigPath = System.getenv("HOME") + "/.kube/config";
        logger.debug("Kube config path: {}", kubeConfigPath);
        namespace = server.cluster.namespace;
        InputStreamReader filereader = new InputStreamReader(new FileInputStream(kubeConfigPath), StandardCharsets.UTF_8);
        KubeConfig kubeConfig = KubeConfig.loadKubeConfig(filereader);
        logger.debug("Getting a refreshed service account access token and its expiration time");
        GoogleCredentials applicationDefaultCredentials = AuthenticationUtils.getServiceAccountCredential(server.testRunnerServiceAccount, AuthenticationUtils.cloudPlatformScope);
        AccessToken accessToken = AuthenticationUtils.getAccessToken(applicationDefaultCredentials);
        Instant tokenExpiration = accessToken.getExpirationTime().toInstant();
        String expiryUTC = tokenExpiration.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        LinkedHashMap<String, Object> authConfigSA = new LinkedHashMap();
        authConfigSA.put("access-token", accessToken.getTokenValue());
        authConfigSA.put("expiry", expiryUTC);
        LinkedHashMap<String, Object> authProviderSA = new LinkedHashMap();
        authProviderSA.put("name", "gcp");
        authProviderSA.put("config", authConfigSA);
        LinkedHashMap<String, Object> userSA = new LinkedHashMap();
        userSA.put("auth-provider", authProviderSA);
        LinkedHashMap<String, Object> userWrapperSA = new LinkedHashMap();
        userWrapperSA.put("name", server.cluster.clusterName);
        userWrapperSA.put("user", userSA);
        ArrayList<Object> usersList = new ArrayList();
        usersList.add(userWrapperSA);
        LinkedHashMap<String, Object> context = new LinkedHashMap();
        context.put("cluster", server.cluster.clusterName);
        context.put("user", server.cluster.clusterName);
        LinkedHashMap<String, Object> contextWrapper = new LinkedHashMap();
        contextWrapper.put("name", server.cluster.clusterName);
        contextWrapper.put("context", context);
        ArrayList<Object> contextsList = new ArrayList();
        contextsList.add(contextWrapper);
        ArrayList<Object> clusters = kubeConfig.getClusters();
        kubeConfig = new KubeConfig(contextsList, clusters, usersList);
        kubeConfig.setContext(server.cluster.clusterName);
        logger.debug("Building the client objects from the config");
        ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();
        Configuration.setDefaultApiClient(client);
        kubernetesClientCoreObject = new CoreV1Api();
        kubernetesClientAppsObject = new AppsV1Api();
    }

    public static List<V1Namespace> listNamespace() throws ApiException {
        V1NamespaceList list;
        list = getKubernetesClientCoreObject().listNamespace((String) null, (Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (Integer)null, (Boolean)null);

        return list.getItems();
    }

    public static List<V1Pod> listPodForAllNamespaces() throws ApiException {
        V1PodList list;
        list = getKubernetesClientCoreObject().listPodForAllNamespaces((Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (String)null, (Integer)null, (Boolean)null);

        return list.getItems();
    }

    public static List<V1Pod> listPods() throws ApiException {
        V1PodList list;
        if (namespace != null && !namespace.isEmpty()) {
            list = getKubernetesClientCoreObject().listNamespacedPod(namespace, (String)null, (Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (Integer)null, (Boolean)null);
        } else {
            list = getKubernetesClientCoreObject().listPodForAllNamespaces((Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (String)null, (Integer)null, (Boolean)null);
        }

        return list.getItems();
    }

    public static List<V1Deployment> listDeploymentForAllNamespaces() throws ApiException {
        V1DeploymentList list;
        list = getKubernetesClientAppsObject().listDeploymentForAllNamespaces((Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (String)null, (Integer)null, (Boolean)null);

        return list.getItems();
    }

    public static List<V1Deployment> listNamespacedDeployment(String namespace) throws ApiException {
        V1DeploymentList list;
        list = getKubernetesClientAppsObject().listNamespacedDeployment(namespace, (String)null, (Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (Integer)null, (Boolean)null);

        return list.getItems();
    }

    public static List<V1Deployment> listDeployments() throws ApiException {
        V1DeploymentList list;
        if (namespace != null && !namespace.isEmpty()) {
            list = getKubernetesClientAppsObject().listNamespacedDeployment(namespace, (String)null, (Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (Integer)null, (Boolean)null);
        } else {
            list = getKubernetesClientAppsObject().listDeploymentForAllNamespaces((Boolean)null, (String)null, (String)null, (String)null, (Integer)null, (String)null, (String)null, (Integer)null, (Boolean)null);
        }

        return list.getItems();
    }

    public static V1Deployment getApiDeployment() throws ApiException {
        return (V1Deployment)listDeployments().stream().filter((deployment) -> {
            String componentLabel = deployment.getMetadata().getLabels().get("app.kubernetes.io/component");
            return (StringUtils.isNotBlank(componentLabel) && componentLabel.equals("workspacemanager"));
        }).findFirst().orElse((V1Deployment)null);
    }

    public static V1Deployment changeReplicaSetSize(V1Deployment deployment, int numberOfReplicas) throws ApiException {
        V1DeploymentSpec existingSpec = deployment.getSpec();
        deployment.setSpec(existingSpec.replicas(numberOfReplicas));
        return getKubernetesClientAppsObject().replaceNamespacedDeployment(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace(), deployment, (String)null, (String)null, (String)null);
    }

    public static void deleteRandomPod() throws ApiException, IOException {
        V1Deployment apiDeployment = getApiDeployment();
        if (apiDeployment == null) {
            throw new RuntimeException("API deployment not found.");
        } else {
            long podCount = getApiPodCount(apiDeployment);
            logger.debug("Pod Count: {}; Message: Before deleting pods", podCount);
            printApiPods(apiDeployment);
            String deploymentComponentLabel = (String)apiDeployment.getMetadata().getLabels().get("app.kubernetes.io/component");
            String randomPodName = ((V1Pod)listPods().stream().filter((pod) -> {
                return deploymentComponentLabel.equals(pod.getMetadata().getLabels().get("app.kubernetes.io/component"));
            }).skip((long)(new Random()).nextInt((int)podCount)).findFirst().get()).getMetadata().getName();
            logger.info("delete random pod: {}", randomPodName);
            deletePod(randomPodName);
        }
    }

    public static void deletePod(String podNameToDelete) throws ApiException, IOException {
        Call call = getKubernetesClientCoreObject().deleteNamespacedPodCall(podNameToDelete, namespace, (String)null, (String)null, (Integer)null, (Boolean)null, (String)null, (V1DeleteOptions)null, (ApiCallback)null);
        Response response = call.execute();
        Configuration.getDefaultApiClient().handleResponse(response, (new TypeToken<V1Pod>() {
        }).getType());
    }

    public static void waitForReplicaSetSizeChange(V1Deployment deployment, int numberOfReplicas) throws Exception {
        long numPods = -1L;
        long numRunningPods = -2L;

        for(int pollCtr = Math.floorDiv(maximumSecondsToWaitForReplicaSetSizeChange, secondsIntervalToPollReplicaSetSizeChange); (numPods != numRunningPods || numPods != (long)numberOfReplicas) && pollCtr >= 0; --pollCtr) {
            TimeUnit.SECONDS.sleep((long)secondsIntervalToPollReplicaSetSizeChange);
            numPods = getApiPodCount(deployment);
            numRunningPods = getApiReadyPods(deployment);
        }

        if (numPods != (long)numberOfReplicas) {
            throw new RuntimeException("Timed out waiting for replica set size to change. (numPods=" + numPods + ", numberOfReplicas=" + numberOfReplicas + ")");
        }
    }

    public static void changeReplicaSetSizeAndWait(int podCount) throws Exception {
        V1Deployment apiDeployment = getApiDeployment();
        if (apiDeployment == null) {
            throw new RuntimeException("API deployment not found.");
        } else {
            long apiPodCount = getApiPodCount(apiDeployment);
            logger.debug("Pod Count: {}; Message: Before scaling pod count", apiPodCount);
            //apiDeployment = changeReplicaSetSize(apiDeployment, podCount);
            //waitForReplicaSetSizeChange(apiDeployment, podCount);
            //apiPodCount = getApiPodCount(apiDeployment);
            logger.debug("Pod Count: {}; Message: After scaling pod count", apiPodCount);
            printApiPods(apiDeployment);
        }
    }

    private static long getApiPodCount(V1Deployment deployment) throws ApiException {
        String deploymentComponentLabel = (String)deployment.getMetadata().getLabels().get("app.kubernetes.io/component");
        long apiPodCount = listPods().stream().filter((pod) -> {
            return deploymentComponentLabel.equals(pod.getMetadata().getLabels().get("app.kubernetes.io/component"));
        }).count();
        return apiPodCount;
    }

    private static long getApiReadyPods(V1Deployment deployment) throws ApiException {
        String deploymentComponentLabel = (String)deployment.getMetadata().getLabels().get("app.kubernetes.io/component");
        long apiPodCount = listPods().stream().filter((pod) -> {
            return deploymentComponentLabel.equals(pod.getMetadata().getLabels().get("app.kubernetes.io/component")) && ((V1ContainerStatus)pod.getStatus().getContainerStatuses().get(0)).getReady();
        }).count();
        return apiPodCount;
    }

    public static void printApiPods(V1Deployment deployment) throws ApiException {
        String deploymentComponentLabel = (String)deployment.getMetadata().getLabels().get("app.kubernetes.io/component");
        listPods().stream().filter((pod) -> {
            return deploymentComponentLabel.equals(pod.getMetadata().getLabels().get("app.kubernetes.io/component"));
        }).forEach((p) -> {
            logger.debug("Pod: {}", p.getMetadata().getName());
        });
    }

    public static void main(String[] args) {
        try {
            String serverPath = "workspace-resiliency.json";
            ServerSpecification server = ServerSpecification.fromJSONFile(serverPath);
            server.testRunnerServiceAccount.validate();
            buildKubernetesClientObject(server);

            List<V1Namespace> namespaces = listNamespace();
            System.out.println(namespaces.size());
            List<V1Pod> pods = listPodForAllNamespaces();
            List<V1Pod> terraPods = pods.stream().filter(p -> p.getMetadata().getNamespace().startsWith("terra-i"))
                    .collect(Collectors.toList());
            System.out.println(terraPods.size());
            List<V1Deployment> deployments = listDeploymentForAllNamespaces();
            List<V1Deployment> terraDeployments = deployments.stream().filter(p -> p.getMetadata().getNamespace().startsWith("terra-i"))
                    .collect(Collectors.toList());
            System.out.println(terraDeployments.size());
            List<V1Deployment> terraNamespacedDeployments = listNamespacedDeployment("terra-ichang");
            System.out.println(terraNamespacedDeployments.size());
            List<V1Deployment> myDeployments = listDeployments();
            System.out.println(myDeployments.size());
            V1Deployment deployment = getApiDeployment();
            System.out.println(deployment.getMetadata().getLabels().get("app.kubernetes.io/component"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
