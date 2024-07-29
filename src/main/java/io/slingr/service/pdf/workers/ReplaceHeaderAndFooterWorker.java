package io.slingr.service.pdf.workers;

import io.slingr.service.pdf.processors.PdfHeaderFooterHandler;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.services.rest.DownloadedFile;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.Strings;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ReplaceHeaderAndFooterWorker extends PdfWorker {
    private final Logger logger = LoggerFactory.getLogger(ReplaceHeaderAndFooterWorker.class);

    private static final String IMAGE_ID = "imageId";
    private static final String HTML = "html";

    public ReplaceHeaderAndFooterWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        super(events, files, appLogger, request);
    }

    @Override
    public void run() {
        Json body = request.getJsonParams();
        String generatedFilePath = null;
        PdfHeaderFooterHandler handler = new PdfHeaderFooterHandler();
        Json settings = body.json("settings");
        Json header = settings.json("header");
        Json footer = settings.json("footer");
        DownloadedFile pdf = files.download(body.string("fileId"));
        if (header != null && header.string(IMAGE_ID) != null || footer != null && footer.string(IMAGE_ID) != null) {
            InputStream headerIs = null;
            if (Objects.requireNonNull(header).string(IMAGE_ID) != null) {
                headerIs = files.download(header.string(IMAGE_ID)).getFile();
            }
            InputStream footerIs = null;
            if (footer.string(IMAGE_ID) != null) {
                footerIs = files.download(footer.string(IMAGE_ID)).getFile();
            }
            generatedFilePath = handler.replaceHeaderAndFooterFromImages(pdf.getFile(), headerIs, footerIs, settings);
        } else if (header != null && header.string(HTML) != null || footer != null && footer.string(HTML) != null) {
            generatedFilePath = handler.replaceHeaderAndFooterFromTemplate(pdf.getFile(), settings);
        }
        if (generatedFilePath != null) {
            InputStream is = null;
            try {
                is = new FileInputStream(generatedFilePath);
                Json fileJson = files.upload("new-file-" + Strings.randomUUIDString(), is, "application/pdf");
                handler.cleanGeneratedFiles();
                Json res = Json.map();
                res.set("status", "ok");
                res.set("file", fileJson);
                events.send("pdfResponse", res, request.getFunctionId());
            } catch (IOException ioe) {
                logger.warn("Can not get generated file");
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException ioe) {
                    logger.error("Can not close temporal to generate file", ioe.fillInStackTrace());
                }
            }
        } else {
            Json res = Json.map();
            res.set("status", "error");
            res.set("message", "Should set images or templates for header and footer");
            events.send("pdfResponse", res, request.getFunctionId());
        }
    }
}
