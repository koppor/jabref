package org.jabref.logic.ai;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;

import org.jabref.logic.JabRefException;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.logic.xmp.XmpUtilReader;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.LinkedFile;
import org.jabref.preferences.FilePreferences;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains a bunch of methods that are useful for loading the documents to AI.
 * <p>
 * This class is an "algorithm class". Meaning it is used in one place and is thrown away quickly.
 */
public class AiIngestor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiIngestor.class.getName());

    public static final int DOCUMENT_SPLITTER_MAX_SEGMENT_SIZE_IN_CHARS = 300;
    public static final int DOCUMENT_SPLITTER_MAX_OVERLAP_SIZE_IN_CHARS = 30;

    // Another "algorithm class" that ingests the contents of the file into the embedding store.
    private final EmbeddingStoreIngestor ingestor;

    public AiIngestor(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        DocumentSplitter documentSplitter = DocumentSplitters
                .recursive(DOCUMENT_SPLITTER_MAX_SEGMENT_SIZE_IN_CHARS, DOCUMENT_SPLITTER_MAX_OVERLAP_SIZE_IN_CHARS);

        this.ingestor = EmbeddingStoreIngestor
                .builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(documentSplitter)
                .build();
    }

    public void ingestLinkedFile(LinkedFile linkedFile, BibDatabaseContext bibDatabaseContext, FilePreferences filePreferences) {
        // TODO: Ingest not only the contents of documents, but also their metadata.
        // This will help the AI to identify a document while performing a QA session over several bib entries.
        // Useful link: https://docs.langchain4j.dev/tutorials/rag/#metadata.

        Optional<Path> path = linkedFile.findIn(bibDatabaseContext, filePreferences);
        if (path.isPresent()) {
            ingestFile(path.get());
        } else {
            LOGGER.error("Could not find path for a linked file: " + linkedFile.getLink());
        }
    }

    public void ingestFile(Path path) {
        if (FileUtil.isPDFFile(path)) {
            ingestPDFFile(path);
        } else {
            LOGGER.info("Usupported file type of file: " + path + ". For now, only PDF files are supported");
        }
    }

    public void ingestPDFFile(Path path) {
        try {
            PDDocument document = new XmpUtilReader().loadWithAutomaticDecryption(path);
            PDFTextStripper stripper = new PDFTextStripper();

            int lastPage = document.getNumberOfPages();
            stripper.setStartPage(1);
            stripper.setEndPage(lastPage);
            StringWriter writer = new StringWriter();
            stripper.writeText(document, writer);

            ingestString(writer.toString());
        } catch (Exception e) {
            LOGGER.error("An error occurred while reading a PDF file: " + path, e);
        }
    }

    public void ingestString(String string) {
        ingestDocument(new Document(string));
    }

    public void ingestDocument(Document document) {
        LOGGER.trace("Ingesting: {}", document);
        ingestor.ingest(document);
    }
}