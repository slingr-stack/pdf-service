package io.slingr.service.pdf.workers;

import io.slingr.service.pdf.processors.PdfFilesUtils;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FillFormWorker extends PdfWorker {
    private final Logger logger = LoggerFactory.getLogger(FillFormWorker.class);

    public FillFormWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        super(events, files, appLogger, request);
        this.pdfFillForm = new PdfFillForm(appLogger);
    }

    @Override
    public void run() {
        fillForm(this.request);
    }

    private void fillForm(FunctionRequest request) {
        Json data = request.getJsonParams();
        String fileId = data.string("fileId");
        if (fileId == null) {
            appLogger.info("Can not find any pdf with null file id");
            return;
        }
        Json res = Json.map();
        InputStream tmpIs = null;
        File temp = null;
        try {
            if (data.contains("settings")) {
                Json settings = data.json("settings");
                temp = pdfFillForm.fillForm(files, fileId, settings);
                if (temp == null) {
                    appLogger.info("Can not generate filled form. Contact the support.");
                    return;
                }
                String fileName = PdfFilesUtils.getFileName("pdf", settings);
                appLogger.info(String.format("Uploading generated file [%s]", fileName));
                tmpIs = new FileInputStream(temp);
                Json fileJson = files.upload(fileName, tmpIs, "application/pdf");
                res.set("status", "ok");
                res.set("file", fileJson);
                events.send("pdfResponse", res, request.getFunctionId());
            } else {
                events.send("pdfResponse", res, request.getFunctionId());
            }
        } catch (IOException e) {
            appLogger.error("Can not generate PDF, I/O exception", e);
            res.set("status", "error");
            res.set("message", "Failed to create file");
            events.send("pdfResponse", res, request.getFunctionId());
        } finally {
            try {
                if (tmpIs != null) {
                    tmpIs.close();
                }
                if (temp != null) {
                    logger.info(String.format("Cleaning files: %b", temp.delete()));
                }
            } catch (IOException ioe) {
                logger.error("Can not close temporal to generate file", ioe.fillInStackTrace());
            }
        }
    }
}
