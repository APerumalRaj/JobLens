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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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
    private Credential getCredentials() throws Exception {
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
            new ByteArrayInputStream(secretJson.getBytes(StandardCharsets.UTF_8))
        );
    }
    
    /**
     * Fetch emails from last 24 hours with job-related keywords
     */
    public List<Message> fetchRecentJobEmails() throws Exception {
        Gmail service = getGmailService();
        List<Message> messages = new ArrayList<>();
        
        // Calculate 24 hours ago timestamp
        long oneDayAgoMs = System.currentTimeMillis() - ChronoUnit.DAYS.getDuration().toMillis();
        long oneDayAgoSecs = oneDayAgoMs / 1000;
        
        // Query: emails from last 24 hours with job-related keywords
        String query = String.format(
            "after:%d (subject:jobs OR subject:recommended OR subject:opportunities)",
            oneDayAgoSecs
        );
        
        log.info("Fetching emails with query: {}", query);
        
        try {
            com.google.api.services.gmail.model.ListMessagesResponse response = service.users()
                .messages()
                .list("me")
                .setQ(query)
                .setMaxResults(20L)
                .execute();
            
            if (response.getMessages() != null) {
                messages.addAll(response.getMessages());
                log.info("Fetched {} emails from Gmail", messages.size());
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
