package com.joblens.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Gmail API integration
 */
@Slf4j
@Service
public class GmailService {
    
    private static final String APPLICATION_NAME = "JobLens Email Filter";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    
    @Value("${gmail.oauth.client-id}")
    private String clientId;
    
    @Value("${gmail.oauth.client-secret}")
    private String clientSecret;
    
    @Value("${gmail.oauth.redirect-uri}")
    private String redirectUri;
    
    @Value("${gmail.oauth.token-file-path}")
    private String tokenFilePath;
    
    @Value("${gmail.oauth.scopes}")
    private List<String> scopes;

    @Value("${job.email.from}")
    private String applicationSenderEmail;

    @Value("${job.email.subject-prefix:JobLens -}")
    private String applicationEmailSubjectPrefix;

    @Value("${gmail.query.subject-terms:jobs,recommended,opportunities}")
    private List<String> querySubjectTerms;

    @Value("${gmail.max-emails-per-run:20}")
    private Long queryMaxResults;

    @Value("${gmail.lookback-hours:24}")
    private Integer lookbackHours;
    
    /**
     * Create Gmail service with authentication
     */
    public Gmail getGmailService() throws Exception {
        return new Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            getCredentials()
        )
        .setApplicationName(APPLICATION_NAME)
        .build();
    }
    
    /**
     * Get or create OAuth2 credentials
     */
    public Credential getCredentials() throws Exception {
        File tokensDirectory = new File(tokenFilePath);
        if (!tokensDirectory.exists()) {
            tokensDirectory.mkdirs();
        }
        
        GoogleClientSecrets clientSecrets = loadClientSecrets();
        
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            clientSecrets,
            scopes
        )
        .setDataStoreFactory(new FileDataStoreFactory(tokensDirectory))
        .setAccessType("offline")
        .build();
        
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
    
    /**
     * Load client secrets from environment variables
     */
    private GoogleClientSecrets loadClientSecrets() throws IOException {
        String secretJson = String.format(
            "{\"installed\":{\"client_id\":\"%s\",\"client_secret\":\"%s\",\"redirect_uris\":[\"%s\"]}}",
            clientId,
            clientSecret,
            redirectUri
        );

        return GoogleClientSecrets.load(
            JSON_FACTORY,
            new InputStreamReader(
                new ByteArrayInputStream(
                    secretJson.getBytes(StandardCharsets.UTF_8)
                ),
                StandardCharsets.UTF_8
            )
        );
    }
    
    /**
     * Build Gmail query for job-related emails and exclude self-generated notifications.
     */
    String buildJobSearchQuery(long afterSeconds) {
        List<String> terms = querySubjectTerms == null || querySubjectTerms.isEmpty()
            ? Arrays.asList("jobs", "recommended", "opportunities")
            : querySubjectTerms;

        String subjectQuery = terms.stream()
            .map(term -> "subject:\"" + term + "\"")
            .collect(Collectors.joining(" OR "));

        String normalizedSubjectPrefix = applicationEmailSubjectPrefix == null
            ? "JobLens -"
            : applicationEmailSubjectPrefix.replaceAll("\"", "").trim();

        return String.format(
            "after:%d (%s) -from:me -from:%s -subject:\"%s\"",
            afterSeconds,
            subjectQuery,
            applicationSenderEmail,
            normalizedSubjectPrefix
        );
    }

    /**
     * Fetch emails from the configured lookback window with job-related keywords.
     */
    public List<Message> fetchRecentJobEmails() throws Exception {
        Gmail service = getGmailService();
        List<Message> messages = new ArrayList<>();

        long afterSeconds = Instant.now()
            .minus(lookbackHours, ChronoUnit.HOURS)
            .getEpochSecond();

        String query = buildJobSearchQuery(afterSeconds);

        log.info("Fetching emails with query: {}", query);

        try {
            com.google.api.services.gmail.model.ListMessagesResponse response = service.users()
                .messages()
                .list("me")
                .setQ(query)
                .setMaxResults(queryMaxResults)
                .execute();

            int skipped = 0;
            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    Message metadataMessage = getMessageMetadata(service, message.getId());
                    if (metadataMessage == null) {
                        continue;
                    }
                    if (isApplicationNotification(metadataMessage)) {
                        skipped++;
                        continue;
                    }
                    messages.add(metadataMessage);
                }
                log.info("Fetched {} emails from Gmail after notification filtering", messages.size());
                if (skipped > 0) {
                    log.info("Skipped {} self-generated notification emails", skipped);
                }
            } else {
                log.info("No emails found matching criteria");
            }
        } catch (Exception e) {
            log.error("Error fetching emails from Gmail", e);
            throw e;
        }

        return messages;
    }

    /**
     * Fetch message metadata required for notification filtering.
     */
    private Message getMessageMetadata(Gmail service, String messageId) {
        try {
            return service.users().messages().get("me", messageId)
                .setFormat("metadata")
                .setMetadataHeaders(Arrays.asList("From", "Subject"))
                .execute();
        } catch (Exception e) {
            log.warn("Unable to fetch metadata for message {}", messageId, e);
            return null;
        }
    }

    /**
     * Determine whether a message is an application notification email.
     */
    public boolean isApplicationNotification(Message message) {
        String subject = getHeaderValue(message, "Subject");
        String from = getHeaderValue(message, "From");

        if (isFromApplicationEmail(from)) {
            return true;
        }

        return isNotificationSubject(subject);
    }

    private boolean isFromApplicationEmail(String fromHeader) {
        if (fromHeader == null || fromHeader.isBlank()) {
            return false;
        }

        String normalizedFrom = fromHeader.toLowerCase();
        String normalizedSender = applicationSenderEmail == null ? "" : applicationSenderEmail.toLowerCase();

        return normalizedFrom.contains(normalizedSender) || normalizedFrom.contains("me");
    }

    private boolean isNotificationSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }

        String normalizedSubject = subject.toLowerCase();
        String normalizedPrefix = applicationEmailSubjectPrefix == null ? "joblens -" : applicationEmailSubjectPrefix.toLowerCase();

        return normalizedSubject.contains(normalizedPrefix);
    }
    
    /**
     * Get full message content
     */
    public Message getFullMessage(String messageId) throws Exception {
        Gmail service = getGmailService();
        try {
            return service.users()
                .messages()
                .get("me", messageId)
                .setFormat("full")
                .execute();
        } catch (Exception e) {
            log.error("Error fetching message {}", messageId, e);
            throw e;
        }
    }
    
    /**
     * Extract body text from message
     */
    public String extractMessageBody(Message message) {
        try {
            MessagePart part = message.getPayload();
            
            // If message has direct body (simple text email)
            if (part.getBody() != null && part.getBody().getData() != null) {
                return decodeBody(part.getBody().getData());
            }
            
            // If message is multipart, find text part
            if (part.getParts() != null) {
                for (MessagePart msgPart : part.getParts()) {
                    if ("text/plain".equals(msgPart.getMimeType()) || "text/html".equals(msgPart.getMimeType())) {
                        if (msgPart.getBody() != null && msgPart.getBody().getData() != null) {
                            return decodeBody(msgPart.getBody().getData());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting message body", e);
        }
        return "";
    }
    
    /**
     * Extract header value
     */
    public String getHeaderValue(Message message, String headerName) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            return message.getPayload().getHeaders().stream()
                .filter(header -> headerName.equalsIgnoreCase(header.getName()))
                .map(com.google.api.services.gmail.model.MessagePartHeader::getValue)
                .findFirst()
                .orElse(null);
        }
        return null;
    }
    
    /**
     * Decode base64 encoded message body
     */
    private String decodeBody(String encodedBody) {
        try {
            return new String(Base64.getUrlDecoder().decode(encodedBody), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Error decoding message body", e);
            return encodedBody;
        }
    }
}
