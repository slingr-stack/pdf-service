package io.slingr.service.pdf;

import io.slingr.services.services.exchange.Parameter;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.tests.ServiceTests;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PdfTest {

    private static final Logger logger = LoggerFactory.getLogger(PdfTest.class);

    private static ServiceTests test;

    @BeforeClass
    public static void init() throws Exception {
        test = ServiceTests.start(new io.slingr.service.pdf.Runner(), "test.properties");
    }

    @Test
    public void testGeneratePdf() {
        logger.info("-- INIT --");
        final Json req = Json.map();

        String headerTemplate = "<!DOCTYPE html>  <html>    <head></head>    <body>      <h2>** Good News: ${title}</h2>      Page <span id='page'></span> of      <span id='topage'></span>         <hr />      <script>         var vars={};        var x=window.location.search.substring(1).split('&');        for (var i in x) {          var z=x[i].split('=',2);          vars[z[0]] = unescape(z[1]);        }        document.getElementById('page').innerHTML = vars.page;        document.getElementById('topage').innerHTML = vars.topage;      </script>     </body>  </html>";

        Json headerData = Json.map().set("title", "Page title!!");
        String footerTemplate = "<!DOCTYPE html><html><body><h3>Page here ## ${name}</h3></body></html>";
        Json footerData = Json.map().set("name", "User Name");

        req.set("template", "<html><body><h1>${title}</h1><#list items as item><tr><td>${item.name}</td></tr></#list></body></html>");
        req.set("data", Json.map()
                .set("title", "Example PDF")
                .set("items", List.of(
                        Json.map().set("name", "Item 1"),
                        Json.map().set("name", "Item 2"))));
        req.set("settings", Json.map()
                .set("name", "MyPdfFile")
                .set("pageSize", "letter")
                .set("headerTemplate", headerTemplate)
                .set("headerData", headerData)
                .set("footerTemplate", footerTemplate)
                .set("footerData", footerData));

        // test request
        Json res = test.executeFunction("generatePdf", req);
        assertNotNull(res);
        logger.info(res.toString());
        assertFalse(res.is(Parameter.EXCEPTION_FLAG));
        // body is 'ok'
        assertNotNull(res.string("status"));
        assertEquals("ok", res.string("status"));

        logger.info("-- END --");
    }

    @Test
    @Ignore
    public void testExtractingText() {
        // Specify the path to the PDF file
        String pdfFilePath = "payroll-sample.pdf";
        // Load the PDF from the classpath
        try (InputStream pdfStream = PdfTest.class.getClassLoader().getResourceAsStream(pdfFilePath)) {
            if (pdfStream == null) {
                System.out.println("PDF not found in classpath.");
                return;
            }

            RandomAccessReadBuffer readBuffer = new RandomAccessReadBuffer(pdfStream);

            // Use Loader.loadPDF to load the PDF from InputStream
            try (PDDocument document = Loader.loadPDF(readBuffer)) {
                if (!document.isEncrypted()) {
                    // Create a PDFTextStripper object to extract text
                    PDFTextStripper pdfStripper = new PDFTextStripper();

                    // Extract text from the PDF
                    String text = pdfStripper.getText(document);

                    // Output the extracted text
                    System.out.println(text);
                } else {
                    System.out.println("Document is encrypted and cannot be read.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPdfToText() throws IOException {
        logger.info("-- INIT --");

        String fileId = "testFileId";
        Json req = Json.map();
        req.set("fileId", fileId);

        ClassLoader classLoader = getClass().getClassLoader();
        InputStream pdfInputStream = classLoader.getResourceAsStream("payroll-sample.pdf");
        PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(pdfInputStream));
        if (!pdf.isEncrypted()) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(pdf);
            assertNotNull("El archivo PDF no se encontró en resources", pdfInputStream);
            assertTrue(text.contains("Pay Period"));
        }
        Json res = test.executeFunction("convertPdfToText", req);
        assertNotNull(res);
        assertNotNull(res.string("status"));
        assertEquals("failed", res.string("status"));
        assertTrue(res.string("message").contains("File does not exist on the application"));
        assertFalse(res.is(Parameter.EXCEPTION_FLAG));
        logger.info(res.toString());
        logger.info("-- END --");
    }
}
