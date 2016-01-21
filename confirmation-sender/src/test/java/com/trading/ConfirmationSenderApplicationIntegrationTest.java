package com.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestTemplate;

import javax.jms.ConnectionFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//@RunWith(SpringJUnit4ClassRunner.class)
public class ConfirmationSenderApplicationIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private BrokerService brokerService;

    @Before
    public void setUp() throws Exception {
        FileSystemUtils.deleteRecursively(new File("activemq-data"));

        brokerService = new BrokerService();
        brokerService.addConnector("tcp://localhost:9999");
        brokerService.start();
    }

    @After
    public void tearDown() throws Exception {
        brokerService.stop();
        FileSystemUtils.deleteRecursively(new File("activemq-data"));
    }

    @Test
    public void consumes_incoming_message_and_send_confirmation() throws Exception {
        ConfirmationSenderApplication.main(new String[0]);

        AllocationReport allocationReport = TestData.allocationReport();

        String allocationReportAsJson = objectMapper.writeValueAsString(allocationReport);

        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory());
        jmsTemplate.send(
                queue(),
                session -> session.createTextMessage(allocationReportAsJson)
        );

        TimeUnit.SECONDS.sleep(5);

        Confirmation confirmation = restTemplate.getForObject(
                "http://confirmation-service.herokuapp.com/api/confirmation?id="
                        + allocationReport.getAllocationId(),
                Confirmation.class
        );

        allocationReport.setMessageStatus(MessageStatus.SENT);
        assertThat(confirmation.getAllocationReport()).isEqualToComparingFieldByField(allocationReport);
    }

    private String queue() {
        return "incoming.allocation.report.queue";
    }

    private ConnectionFactory connectionFactory() {

        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
        activeMQConnectionFactory.setBrokerURL("tcp://localhost:9999");

        SingleConnectionFactory factory = new SingleConnectionFactory();
        factory.setTargetConnectionFactory(activeMQConnectionFactory);
        return factory;
    }
}
