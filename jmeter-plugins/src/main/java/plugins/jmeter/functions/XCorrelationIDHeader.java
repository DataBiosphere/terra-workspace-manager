package plugins.jmeter.functions;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import org.hashids.Hashids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XCorrelationIDHeader extends AbstractFunction {
    private Hashids hashids = new Hashids("XCorrelatiomnIDSalt", 8);
    private static final String MDC_CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(XCorrelationIDHeader.class);
    private static final List<String> desc = new LinkedList<>();

    private static final String KEY = "__XCorrelationIDHeader"; //$NON-NLS-1$

    // Number of parameters expected - used to reject invalid calls
    private static final int MIN_PARAMETER_COUNT = 1;
    private static final int MAX_PARAMETER_COUNT = 2;

    static {
        desc.add(getResString("function_name_paropt")); //$NON-NLS-1$
    }

    private Object[] values;

    public XCorrelationIDHeader() {

    }

    @Override
    public List<String> getArgumentDesc() {
        return desc;
    }

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
        StringBuilder header = new StringBuilder();
        header.append(MDC_CORRELATION_ID_HEADER);
        header.append(": ");
        String correlationId = generateCorrelationId();
        String prefix = ((CompoundVariable) values[0]).execute().trim();
        if (prefix.length() > 0) {
            header.append(prefix);
            header.append("-");
        }
        header.append(correlationId);

        JMeterVariables vars = getVariables();

        String varName = null;

        if (values.length > 1)
            varName = ((CompoundVariable) values[1]).execute().trim();

        if (vars != null && varName != null) {
            vars.put(varName, header.toString());
        }

        return header.toString();
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
        checkParameterCount(parameters, MIN_PARAMETER_COUNT, MAX_PARAMETER_COUNT);
        values = parameters.toArray();
    }

    @Override
    public String getReferenceKey() {
        return KEY;
    }

    private static String getResString(String key) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages");

        return bundle.containsKey(key) ? bundle.getString(key) : String.format("[res_key=\"%s\"]", key);
    }

    private String generateCorrelationId() {
        long generatedLong = ThreadLocalRandom.current().nextLong(0, Integer.MAX_VALUE);

        return hashids.encode(generatedLong);
    }
}
