package org.logstash.javaapi;

import co.elastic.logstash.api.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.logstash.execution.queue.QueueWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaInputExampleTest {

    @Test
    public void testJavaInputExample() {
        String prefix = "This is message";
        long eventCount = 5;
        Map<String, Object> configValues = new HashMap<>();
        configValues.put(JavaInputExample.PREFIX_CONFIG.name(), prefix);
        configValues.put(JavaInputExample.EVENT_COUNT_CONFIG.name(), eventCount);
        Configuration config = new Configuration(configValues);
        JavaInputExample input = new JavaInputExample(config, null);
        TestQueueWriter testQueueWriter = new TestQueueWriter();
        input.start(testQueueWriter);

        List<Map<String, Object>> events = testQueueWriter.getEvents();
        Assert.assertEquals(eventCount, events.size());
        for (int k = 1; k <= events.size(); k++) {
            Assert.assertEquals(prefix + " " + StringUtils.center(k + " of " + eventCount, 20),
                    events.get(k - 1).get("message"));
        }
    }
}

class TestQueueWriter implements QueueWriter {

    private List<Map<String, Object>> events = new ArrayList<>();

    @Override
    public void push(Map<String, Object> event) {
        synchronized (this) {
            events.add(event);
        }
    }

    public List<Map<String, Object>> getEvents() {
        return events;
    }
}
