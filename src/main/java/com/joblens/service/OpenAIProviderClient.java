package com.joblens.service;

/**
 * Abstraction for AI provider APIs so the platform can replace OpenAI with another provider later.
 */
public interface OpenAIProviderClient {

    boolean isEnabled();

    String complete(String prompt, int maxTokens) throws Exception;
}
