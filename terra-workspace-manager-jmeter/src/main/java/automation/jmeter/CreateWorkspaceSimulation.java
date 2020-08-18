package automation.jmeter;

import org.apache.jmeter.config.Argument;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.config.gui.ArgumentsPanel;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.gui.LoopControlPanel;
import org.apache.jmeter.control.gui.TestPlanGui;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.modifiers.JSR223PreProcessor;
import org.apache.jmeter.modifiers.UserParameters;
import org.apache.jmeter.modifiers.gui.UserParametersGui;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.control.gui.HttpTestSampleGui;
import org.apache.jmeter.protocol.http.gui.HeaderPanel;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.reporters.Summariser;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.threads.SetupThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.threads.gui.SetupThreadGroupGui;
import org.apache.jmeter.threads.gui.ThreadGroupGui;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.backend.BackendListener;
import org.apache.jmeter.visualizers.backend.BackendListenerGui;
import org.apache.jorphan.collections.HashTree;

import java.io.File;
import java.io.FileOutputStream;

public class CreateWorkspaceSimulation {
    private static StandardJMeterEngine jMeterEngine = new StandardJMeterEngine();

    private static TestPlan testPlan;
    private static SetupThreadGroup setupThreadGroup;
    private static LoopController setUpLoopController;
    private static JSR223PreProcessor jsr223PreProcessor;
    private static ThreadGroup threadGroup;
    private static LoopController loopController;
    private static HTTPSamplerProxy httpSamplerProxy;
    private static HashTree testPlanTree;
    private static HeaderManager headerManager;
    private static UserParameters userParameters;
    private static BackendListener backendListener;

    // TODO: All JMeter test has a root test plan node
    // much of this is boilerplate code. Refactoring
    // will be taken up in a separate Jira ticket.
    private static void initializeTestPlan(String testPlanName) {
        testPlan = new TestPlan(testPlanName);
        testPlan.setComment("");
        testPlan.setFunctionalMode(false);
        testPlan.setTearDownOnShutdown(true);
        testPlan.setSerialized(false);
        testPlan.setUserDefinedVariables((Arguments) new ArgumentsPanel().createTestElement());
        testPlan.setProperty(TestElement.TEST_CLASS, TestPlan.class.getName());
        testPlan.setProperty(TestElement.GUI_CLASS, TestPlanGui.class.getName());
    }

    private static void setLoopController(int loopCount) {
        loopController = new LoopController();
        loopController.setLoops(loopCount);
        loopController.setFirst(true);
        loopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        loopController.setProperty(TestElement.GUI_CLASS, LoopControlPanel.class.getName());
        loopController.initialize();
    }

    private static void setUpThreadGroup() {
        setupThreadGroup = new SetupThreadGroup();
        setupThreadGroup.setName("setUp Thread Group");
        setupThreadGroup.setEnabled(true);
        setupThreadGroup.setNumThreads(1);
        setupThreadGroup.setRampUp(1);
        setupThreadGroup.setScheduler(false);
        setupThreadGroup.setIsSameUserOnNextIteration(true);
        setupThreadGroup.setProperty(SetupThreadGroup.ON_SAMPLE_ERROR, SetupThreadGroup.ON_SAMPLE_ERROR_CONTINUE);
        setupThreadGroup.setSamplerController(setUpLoopController);
        setupThreadGroup.setProperty(TestElement.TEST_CLASS, SetupThreadGroup.class.getName());
        setupThreadGroup.setProperty(TestElement.GUI_CLASS, SetupThreadGroupGui.class.getName());
    }

    private static void setUpLoopController() {
        setUpLoopController = new LoopController();
        setUpLoopController.setName("setUp Loop controller");
        setUpLoopController.setLoops(1);
        setUpLoopController.setContinueForever(false);
        setUpLoopController.setFirst(true);
        setUpLoopController.setProperty(TestElement.TEST_CLASS, LoopController.class.getName());
        setUpLoopController.setProperty(TestElement.GUI_CLASS, LoopControlPanel.class.getName());
        setUpLoopController.initialize();
    }

    private static void setJSR223PreProcessor() {
        jsr223PreProcessor = new JSR223PreProcessor();
        jsr223PreProcessor.setName("JSR223 Pre Processor");
        jsr223PreProcessor.setEnabled(true);
        jsr223PreProcessor.setProperty("cacheKey", "true");
        jsr223PreProcessor.setProperty("script", "${__setProperty(oauth2Token, ${__OAuth2Token(GCP, FIRECLOUD_SERVICE_ACCOUNT_CREDS, SAM_USER)})}");
        jsr223PreProcessor.setProperty("scriptLanguage", "groovy");
        jsr223PreProcessor.setProperty(TestElement.TEST_CLASS, JSR223PreProcessor.class.getName());
        jsr223PreProcessor.setProperty(TestElement.GUI_CLASS, TestBeanGUI.class.getName());
    }

    private static void setThreadGroup(int noOfThreads, int setRamupNo) {
        threadGroup = new ThreadGroup();
        threadGroup.setName("CreateWorkspace Thread Group");
        threadGroup.setNumThreads(noOfThreads);
        threadGroup.setRampUp(setRamupNo);
        threadGroup.setScheduler(false);
        threadGroup.setIsSameUserOnNextIteration(false);
        threadGroup.setProperty(ThreadGroup.ON_SAMPLE_ERROR, ThreadGroup.ON_SAMPLE_ERROR_CONTINUE);
        threadGroup.setSamplerController(loopController);
        threadGroup.setProperty(TestElement.TEST_CLASS, ThreadGroup.class.getName());
        threadGroup.setProperty(TestElement.GUI_CLASS, ThreadGroupGui.class.getName());
    }

    private static void setHttpSampler(String requestType, String setPath) {
        httpSamplerProxy = new HTTPSamplerProxy();
        httpSamplerProxy.setDomain("${__env(WSM_SERVER)}");
        httpSamplerProxy.setProperty("HTTPSampler.port", "${__env(WSM_PORT)}");
        httpSamplerProxy.setProperty("HTTPSampler.protocol", "${__env(WSM_PROTOCOL)}");
        httpSamplerProxy.setPath(setPath);
        httpSamplerProxy.setMethod(requestType);
        String body = "{" + "\"id\"" + ":" + " \"" + "${workspaceId}" + "\", " + "\"authToken\"" + ":" + " \""
                + "${__P(oauth2Token)}" + "\", " + "\"spendProfile\"" + ":" + " \"" + "${workspaceId}" + "\", " + "\"policies\""
                + ":" + "[ \"" + "${workspaceId}" + "\" ]" + "}";
        httpSamplerProxy.addNonEncodedArgument("", body, "=");
        httpSamplerProxy.setPostBodyRaw(true);
        httpSamplerProxy.setFollowRedirects(true);
        httpSamplerProxy.setAutoRedirects(false);
        httpSamplerProxy.setUseKeepAlive(true);
        httpSamplerProxy.setDoMultipartPost(false);
        httpSamplerProxy.setName("CreateWorkspaceSimulation");
        httpSamplerProxy.setProperty(TestElement.TEST_CLASS, HTTPSamplerProxy.class.getName());
        httpSamplerProxy.setProperty(TestElement.GUI_CLASS, HttpTestSampleGui.class.getName());
    }

    private static void setHeaders() {
        headerManager = new HeaderManager();
        headerManager.setName("HTTP Header Manager");
        headerManager.add(new Header("Content-type", "application/json"));
        headerManager.add(new Header("Authorization", "Bearer ${__P(oauth2Token)}"));
        headerManager.add(new Header("accept", "application/json"));
        headerManager.setProperty(TestElement.TEST_CLASS, HeaderManager.class.getName());
        headerManager.setProperty(TestElement.GUI_CLASS, HeaderPanel.class.getName());
    }

    private static void setUserParameters() {
        userParameters = new UserParameters();
        userParameters.setName("User Parameters UUID");
        userParameters.setComment("TestPlan.comments");

        CollectionProperty namesProp = new CollectionProperty();
        namesProp.setName("UserParameters.names");
        StringProperty workspaceIdProp = new StringProperty("-836030906", "workspaceId");
        namesProp.addProperty(workspaceIdProp);
        userParameters.setNames(namesProp);

        CollectionProperty threadListsProp = new CollectionProperty();
        threadListsProp.setName("UserParameters.thread_values");
        CollectionProperty threadValuesProp = new CollectionProperty();
        threadValuesProp.setName("681405977");
        StringProperty uuidProp = new StringProperty("118040362", "${__UUID}");
        threadValuesProp.addProperty(uuidProp);
        threadListsProp.addProperty(threadValuesProp);
        userParameters.setThreadLists(threadListsProp);

        userParameters.setPerIteration(true);

        userParameters.setProperty(TestElement.TEST_CLASS, UserParameters.class.getName());
        userParameters.setProperty(TestElement.GUI_CLASS, UserParametersGui.class.getName());
        userParameters.setEnabled(true);
    }

    private static void setupBackendListeners() {
        backendListener = new BackendListener();
        backendListener.setProperty(TestElement.TEST_CLASS, BackendListener.class.getName());
        backendListener.setProperty(TestElement.GUI_CLASS, BackendListenerGui.class.getName());
        backendListener.setName("Backend Listener");
        backendListener.setEnabled(true);
        backendListener.setClassname("org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient");
        backendListener.setQueueSize("5000");

        CollectionProperty props = new CollectionProperty();
        props.setName("Arguments.arguments");

        Argument influxdbMetricsSender = new Argument("influxdbMetricsSender",
                "org.apache.jmeter.visualizers.backend.influxdb.HttpMetricsSender", "=");
        Argument influxdbUrl = new Argument("influxdbUrl", "${__env(INFLUXDB_WSM_URL)}", "=");
        Argument application = new Argument("application", "Terra Workspace Manager", "=");
        Argument measurement = new Argument("measurement", "${__env(INFLUXDB_WSM_MEASUREMENT)}", "=");
        Argument summaryOnly = new Argument("summaryOnly", "false", "=");
        Argument samplersRegex = new Argument("samplersRegex", ".*", "=");
        Argument percentiles = new Argument("percentiles", "90;95;99", "=");
        Argument testTitle = new Argument("testTitle", "Test name", "=");
        Argument eventTags = new Argument("eventTags", "", "=");

        props.addItem(influxdbMetricsSender);
        props.addItem(influxdbUrl);
        props.addItem(application);
        props.addItem(measurement);
        props.addItem(summaryOnly);
        props.addItem(samplersRegex);
        props.addItem(percentiles);
        props.addItem(testTitle);
        props.addItem(eventTags);

        Arguments arguments = new Arguments();
        arguments.setName("arguments");
        arguments.setEnabled(true);
        arguments.setProperty(TestElement.TEST_CLASS, Arguments.class.getName());
        arguments.setProperty(TestElement.GUI_CLASS, ArgumentsPanel.class.getName());
        arguments.setProperty(props);

        backendListener.setArguments(arguments);
    }

    private static HashTree configureTestPlan() {
        testPlanTree = new HashTree();
        testPlanTree.add(testPlan);

        HashTree setUpThreadGroupTree = new HashTree();
        setUpThreadGroupTree.add(threadGroup);
        setUpThreadGroupTree.add(setupThreadGroup);

        HashTree parametersTree = new HashTree();
        parametersTree.add(headerManager);
        parametersTree.add(userParameters);
        parametersTree.add(backendListener);

        HashTree httpSamplerTree = new HashTree();
        httpSamplerTree.add(httpSamplerProxy);
        httpSamplerTree.add(httpSamplerProxy, parametersTree);

        setUpThreadGroupTree.add(threadGroup, httpSamplerTree);

        HashTree jsr223PreProcessorTree = new HashTree();
        jsr223PreProcessorTree.add(jsr223PreProcessor);
        setUpThreadGroupTree.add(setupThreadGroup, jsr223PreProcessorTree);

        testPlanTree.add(testPlan, setUpThreadGroupTree);

        return testPlanTree;
    }

    public static void main(String[] argv) throws Exception {

        // for capturing HTTP traffic
        // System.setProperty("http.proxyHost", "127.0.0.1");
        // System.setProperty("http.proxyPort", "8888");
        // for capturing HTTPS traffic
        // System.setProperty("https.proxyHost", "127.0.0.1");
        // System.setProperty("https.proxyPort", "8888");

        // TODO: Replace hardcoded location of JMeter installation
        // by JMETER_HOME envvar
        File jmeterHome = new File("/apache-jmeter-5.3");
        String slash = System.getProperty("file.separator");

        File jmeterProperties = new File(jmeterHome.getPath() + slash + "bin" + slash + "jmeter.properties");

        // JMeter initialization (properties, log levels, locale, etc)
        JMeterUtils.setJMeterHome(jmeterHome.getPath());
        JMeterUtils.loadJMeterProperties(jmeterProperties.getPath());
        JMeterUtils.initLogging();// you can comment this line out to see extra log messages of i.e. DEBUG level
        JMeterUtils.initLocale();

        initializeTestPlan("Terra Workspace Manager Test Plan");
        setUpLoopController();
        setUpThreadGroup();
        setJSR223PreProcessor();
        setLoopController(1);
        setThreadGroup(2, 2);
        setHttpSampler("POST", "api/workspaces/v1");
        setHeaders();
        setUserParameters();
        setupBackendListeners();
        configureTestPlan();

        // save generated test plan to JMeter's .jmx file format
        SaveService.saveTree(testPlanTree, new FileOutputStream(jmeterHome + slash + "createworkspace.jmx"));

        // add Summarizer output to get test progress in stdout like:
        // summary = 2 in 1.3s = 1.5/s Avg: 631 Min: 290 Max: 973 Err: 0 (0.00%)
        Summariser summer = null;
        String summariserName = JMeterUtils.getPropDefault("summariser.name", "summary");
        if (summariserName.length() > 0) {
            summer = new Summariser(summariserName);
        }

        // Store execution results into a .jtl file
        String logFile = jmeterHome + slash + "createworkspace.jtl";
        ResultCollector logger = new ResultCollector(summer);
        logger.setFilename(logFile);
        testPlanTree.add(testPlanTree.getArray()[0], logger);

        // Run Test Plan
        //jMeterEngine.configure(testPlanTree);
        //jMeterEngine.run();

        // System.out.println("Test completed. See createworkspace.jtl file for results");
        System.out.println("Open createworkspacec.jmx file in JMeter GUI to validate the code");
    }
}