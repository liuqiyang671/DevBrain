package edu.cqupt.devbrain.knowledge.service.validator;

import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.knowledge.config.UploadProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUploadValidatorTest {

    private FileUploadValidator validator;

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        validator = new FileUploadValidator(properties);
        validator.init();
    }

    // ========== validate ==========

    @Test
    void validateRejectsNullFile() {
        assertThrows(ClientException.class, () -> validator.validate(null));
    }

    @Test
    void validateRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        assertThrows(ClientException.class, () -> validator.validate(file));
    }

    @Test
    void validateRejectsBlankFilename() {
        MockMultipartFile file = new MockMultipartFile("file", "   ", "application/pdf", "content".getBytes());
        assertThrows(ClientException.class, () -> validator.validate(file));
    }

    @Test
    void validateRejectsZeroSizeFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        // empty file is caught first
        assertThrows(ClientException.class, () -> validator.validate(file));
    }

    @Test
    void validateAcceptsValidFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        assertDoesNotThrow(() -> validator.validate(file));
    }

    // ========== sanitizeFilename ==========

    @Test
    void sanitizeRemovesPathTraversal() {
        assertEquals("test.pdf", validator.sanitizeFilename("../../../test.pdf"));
        assertEquals("test.pdf", validator.sanitizeFilename("..\\..\\test.pdf"));
        assertEquals("test.pdf", validator.sanitizeFilename("/var/tmp/test.pdf"));
        assertEquals("test.pdf", validator.sanitizeFilename("C:\\Users\\test.pdf"));
    }

    @Test
    void sanitizeRemovesNullBytes() {
        assertEquals("test.pdf", validator.sanitizeFilename("test\0.pdf"));
    }

    @Test
    void sanitizeRejectsNullFilename() {
        assertThrows(ClientException.class, () -> validator.sanitizeFilename(null));
    }

    @Test
    void sanitizeRejectsBlankFilename() {
        assertThrows(ClientException.class, () -> validator.sanitizeFilename("   "));
    }

    @Test
    void sanitizePreservesCleanFilename() {
        assertEquals("document.docx", validator.sanitizeFilename("document.docx"));
    }

    // ========== extractExtension ==========

    @Test
    void extractExtensionReturnsLowercase() {
        assertEquals("pdf", validator.extractExtension("test.PDF"));
        assertEquals("docx", validator.extractExtension("file.DOCX"));
    }

    @Test
    void extractExtensionReturnsEmptyForNoExtension() {
        assertEquals("", validator.extractExtension("Makefile"));
        assertEquals("", validator.extractExtension("file."));
    }

    @Test
    void extractExtensionHandlesNull() {
        assertEquals("", validator.extractExtension(null));
    }

    // ========== validateFileType ==========

    @Test
    void validateFileTypeAcceptsWhitelistedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "x".getBytes());
        assertDoesNotThrow(() -> validator.validateFileType(file, "pdf"));
    }

    @Test
    void validateFileTypeRejectsBlockedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());
        assertThrows(ClientException.class, () -> validator.validateFileType(file, "exe"));
    }

    @Test
    void validateFileTypeRejectsUnknownExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "data.xyz", "application/octet-stream", "x".getBytes());
        assertThrows(ClientException.class, () -> validator.validateFileType(file, "xyz"));
    }

    @Test
    void validateFileTypeRejectsEmptyExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "Makefile", "text/plain", "x".getBytes());
        assertThrows(ClientException.class, () -> validator.validateFileType(file, ""));
    }
}
