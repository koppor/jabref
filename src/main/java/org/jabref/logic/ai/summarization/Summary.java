package org.jabref.logic.ai.summarization;

import org.jabref.model.ai.AiProvider;

import java.io.Serializable;
import java.time.LocalDateTime;

public record Summary(LocalDateTime timestamp, AiProvider aiProvider, String model, String content)
        implements Serializable {}
