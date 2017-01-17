package org.radarcns.integration;

import junit.framework.TestCase;

import org.apache.avro.specific.SpecificRecord;
import org.radarcns.data.AvroEncoder;
import org.radarcns.data.SpecificRecordEncoder;

import org.radarcns.kafka.KafkaSender;
import org.radarcns.kafka.SchemaRetriever;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.radarcns.kafka.rest.RestSender;
import org.radarcns.kafka.rest.ThreadedKafkaSender;
import org.radarcns.key.MeasurementKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadedKafkaSenderTest extends TestCase {
    private Logger logger = LoggerFactory.getLogger(ThreadedKafkaSenderTest.class);

    public void testMain() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/org/radarcns/kafka/kafka.properties")) {
            assertNotNull(in);
            props.load(in);
        }

        if (!Boolean.parseBoolean(props.getProperty("servertest","false"))) {
            logger.info("Serve test case has been disable.");
            return;
        }

        int numberOfDevices = 50;

        logger.info("Simulating the load of {}", numberOfDevices);
        MockDevice[] threads = new MockDevice[numberOfDevices];

        SchemaRetriever schemaRetriever = new SchemaRetriever(props.getProperty("schema.registry.url"));
        URL kafkaURL = new URL(props.getProperty("rest.proxy.url"));

        AvroEncoder keyEncoder = new SpecificRecordEncoder(false);
        AvroEncoder valueEncoder = new SpecificRecordEncoder(false);

        KafkaSender<MeasurementKey, SpecificRecord> directSender = new RestSender<>(kafkaURL, schemaRetriever, keyEncoder, valueEncoder, 2500);
        KafkaSender<MeasurementKey, SpecificRecord> sender = new ThreadedKafkaSender<>(directSender);
        sender.resetConnection();

        String userID = "UserID_";
        String sourceID = "SourceID_";

        try {
            for (int i = 0; i < numberOfDevices; i++) {
                threads[i] = new MockDevice<>(sender, new MeasurementKey(userID+i, sourceID+i), MeasurementKey.getClassSchema(), MeasurementKey.class);
                threads[i].start();
            }
            // stop running after 5 seconds, or after the first thread quits, whichever comes first
            long streamingTimeoutMs = 60_000L;
            if (props.containsKey("streaming.timeout.ms")) {
                try {
                    streamingTimeoutMs = Long.parseLong(props.getProperty("streaming.timeout.ms"));
                } catch (NumberFormatException ex) {
                    // whatever
                }
            }
            threads[0].join(streamingTimeoutMs);
            for (MockDevice device : threads) {
                device.interrupt();
            }
            for (MockDevice device : threads) {
                device.join();
            }
            for (MockDevice device : threads) {
                assertNull("Device had IOException", device.getException());
            }
        }
        catch(Exception ex){

        }
    }
}