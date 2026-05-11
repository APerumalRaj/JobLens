package com.joblens.service;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class GmailServiceTest {

    @Test
    void testBuildJobSearchQueryExcludesAppNotifications() {
        GmailService service = new GmailService();
        ReflectionTestUtils.setField(service, "applicationSenderEmail", "perumal@example.com");
        ReflectionTestUtils.setField(service, "applicationEmailSubjectPrefix", "JobLens -");
        ReflectionTestUtils.setField(service, "querySubjectTerms", Arrays.asList("jobs", "recommended", "opportunities"));

        String query = service.buildJobSearchQuery(1700000000L);

        assertTrue(query.contains("after:1700000000"));
        assertTrue(query.contains("-from:me"));
        assertTrue(query.contains("-from:perumal@example.com"));
        assertTrue(query.contains("-subject:\"JobLens -\""));
    }

    @Test
    void testIsApplicationNotificationReturnsTrueForSelfGeneratedEmail() {
        GmailService service = new GmailService();
        ReflectionTestUtils.setField(service, "applicationSenderEmail", "perumal@example.com");
        ReflectionTestUtils.setField(service, "applicationEmailSubjectPrefix", "JobLens -");

        Message message = new Message();
        MessagePart payload = new MessagePart();
        payload.setHeaders(Arrays.asList(
            new MessagePartHeader().setName("From").setValue("JobLens <perumal@example.com>"),
            new MessagePartHeader().setName("Subject").setValue("JobLens - Top 10 Opportunities (Last 24h)")
        ));
        message.setPayload(payload);

        assertTrue(service.isApplicationNotification(message));
    }

    @Test
    void testIsApplicationNotificationReturnsFalseForJobEmail() {
        GmailService service = new GmailService();
        ReflectionTestUtils.setField(service, "applicationSenderEmail", "perumal@example.com");
        ReflectionTestUtils.setField(service, "applicationEmailSubjectPrefix", "JobLens -");

        Message message = new Message();
        MessagePart payload = new MessagePart();
        payload.setHeaders(Arrays.asList(
            new MessagePartHeader().setName("From").setValue("recruiter@company.com"),
            new MessagePartHeader().setName("Subject").setValue("Backend opportunity at Company")
        ));
        message.setPayload(payload);

        assertFalse(service.isApplicationNotification(message));
    }
}
