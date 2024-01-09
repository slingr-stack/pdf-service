package io.slingr.service.pdf;

import io.slingr.services.services.exchange.Parameter;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.tests.ServiceTests;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

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
}
