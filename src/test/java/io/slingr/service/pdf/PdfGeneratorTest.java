package io.slingr.service.pdf;

import io.slingr.service.pdf.processors.PdfHeaderFooterHandler;
import io.slingr.services.utils.FilesUtils;
import io.slingr.services.utils.Json;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class PdfGeneratorTest {

    private final String PDF_FILE = "report.pdf";

    @Before
    public void init() { }

    @Test
    public void testChangeHeader() throws IOException {
        String headerHtml = "<!DOCTYPE html><html><body><h2>Title: ## ${name}</h2></body></html>";
        Json headerData = Json.map().set("name", "This is an example");

        String footerHtml = "<!DOCTYPE html><html><body><small>Page here ## ${name}</small></body></html>";
        Json footerData = Json.map().set("name", "SLINGR 2018");

        InputStream file = FilesUtils.getInternalFile(PDF_FILE);

        Json settings = Json.map();
        Json header = Json.map();
        header.set("html", headerHtml);
        header.set("data", headerData);
        header.set("height", 120);
        settings.set("header", header);

        Json footer = Json.map();
        footer.set("html", footerHtml);
        footer.set("data", footerData);
        footer.set("height", 75);
        settings.set("footer", footer);
        PdfHeaderFooterHandler pdfUtils = new PdfHeaderFooterHandler();

        // local: sudo cp wkhtmltoimage /usr/bin/wkhtmltoimage && sudo cp wkhtmltoimage /usr/bin/wkhtmltoimage
        String path = pdfUtils.replaceHeaderAndFooterFromTemplate(file, settings);
        Assert.assertNotNull(path);

        pdfUtils.cleanGeneratedFiles();
    }

    @Test
    public void testChangeHeaderWithImages() throws IOException {
        InputStream file = FilesUtils.getInternalFile(PDF_FILE);

        Json settings = Json.map();
        Json header = Json.map();
        header.set("height", 120);
        settings.set("header", header);
        Json footer = Json.map();
        footer.set("height", 75);
        settings.set("footer", footer);

        String HEADER_FILE = "global-header.png";
        InputStream headerIs = FilesUtils.getInternalFile(HEADER_FILE);
        String FOOTER_FILE = "global-footer.png";
        InputStream footerIs = FilesUtils.getInternalFile(FOOTER_FILE);

        PdfHeaderFooterHandler pdfUtils = new PdfHeaderFooterHandler();
        String path = pdfUtils.replaceHeaderAndFooterFromImages(file, headerIs, footerIs, settings);
        Assert.assertNotNull(path);

        pdfUtils.cleanGeneratedFiles();
    }
}
