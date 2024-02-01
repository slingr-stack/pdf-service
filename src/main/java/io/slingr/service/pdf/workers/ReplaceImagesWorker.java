package io.slingr.service.pdf.workers;

import io.slingr.service.pdf.processors.PdfFilesUtils;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

public class ReplaceImagesWorker extends PdfImageWorker {

    public ReplaceImagesWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        super(events, files, appLogger, request);
    }

    @Override
    public void run() {
        Json data = request.getJsonParams();
        String requestId = request.getFunctionId();
        String fileId = data.string("fileId");
        Json res = Json.map();
        try {
            if (data.contains("settings")) {
                InputStream is = files.download(fileId).getFile();
                PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(is));
                Json settings = data.json("settings");
                if (settings.contains("images")) {
                    List<Json> settingsImages = settings.jsons("images");
                    for (Json image : settingsImages) {
                        if (image.contains("index") && image.contains("fileId")) {
                            int index = image.integer("index");
                            replaceImageInPdf(pdf, image.string("fileId"), index);
                        }
                    }
                }
                File temp = File.createTempFile("pdf-images-" + new Date().getTime(), ".pdf");
                pdf.save(temp);
                pdf.close();
                String fileName = PdfFilesUtils.getFileName("pdf", settings);
                Json fileJson = files.upload(fileName, new FileInputStream(temp), "application/pdf");
                res.set("status", "ok");
                res.set("file", fileJson);
                events.send("pdfResponse", res, requestId);
            } else {
                events.send("pdfResponse", res, requestId);
            }
        } catch (IOException e) {
            appLogger.info("Can not generate PDF, I/O exception", e);
            res.set("status", "error");
            res.set("message", "Failed to create file");
            events.send("pdfResponse", res, requestId);
        }
    }
}
