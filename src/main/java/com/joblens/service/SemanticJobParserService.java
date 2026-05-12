package com.joblens.service;

import com.joblens.dto.JobDTO;
import com.joblens.dto.JobPage;

/**
 * AI-assisted parser for job pages.
 */
public interface SemanticJobParserService {
    JobDTO parseJob(JobPage jobPage);
}
