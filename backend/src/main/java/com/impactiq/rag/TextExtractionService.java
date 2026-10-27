package com.impactiq.rag;

import com.impactiq.config.KnowledgeProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text page by page. Text formats become a single page without a page number.
 * Scanned (image-only) PDFs are rejected with a clear message: OCR is intentionally not bundled.
 */
@Service
public class TextExtractionService {

    public static final String SCANNED_PDF_MESSAGE =
            "No extractable text found. This looks like a scanned or image-only PDF; OCR is not supported. "
                    + "Upload a text-based PDF or a TXT/MD export instead.";

    private final KnowledgeProperties properties;
    private final FileValidationService fileValidationService;

    public TextExtractionService(KnowledgeProperties properties, FileValidationService fileValidationService) {
        this.properties = properties;
        this.fileValidationService = fileValidationService;
    }

    public ExtractedText extract(ValidatedFile file) {
        List<ExtractedText.Page> pages = file.isPdf() ? extractPdf(file.bytes()) : List.of(
                new ExtractedText.Page(null, normalize(fileValidationService.decodeStrictUtf8(file.bytes()))));
        int total = pages.stream().mapToInt(page -> page.text().length()).sum();
        if (total > properties.getMaxExtractedChars()) {
            throw new ExtractionException("Extracted text exceeds the " + properties.getMaxExtractedChars() + " character limit");
        }
        if (pages.stream().allMatch(page -> page.text().isBlank())) {
            throw new ExtractionException(file.isPdf() ? SCANNED_PDF_MESSAGE : "The file contains no text");
        }
        return new ExtractedText(pages, file.isPdf() ? pages.size() : null);
    }

    private List<ExtractedText.Page> extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new ExtractionException("Encrypted PDFs are not supported");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > properties.getMaxPdfPages()) {
                throw new ExtractionException("PDF has " + pageCount + " pages; the limit is " + properties.getMaxPdfPages());
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<ExtractedText.Page> pages = new ArrayList<>(pageCount);
            for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                pages.add(new ExtractedText.Page(pageNumber, normalize(stripper.getText(document))));
            }
            return pages;
        } catch (InvalidPasswordException exception) {
            throw new ExtractionException("Password-protected PDFs are not supported");
        } catch (IOException exception) {
            throw new ExtractionException("The PDF could not be parsed");
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
    }

    public record ExtractedText(List<Page> pages, Integer pageCount) {

        public record Page(Integer pageNumber, String text) {
        }

        public String fullText() {
            return String.join("\n", pages.stream().map(Page::text).toList());
        }
    }

    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message) {
            super(message);
        }
    }
}
