package io.slingr.service.pdf.workers;

import io.slingr.service.pdf.processors.PdfFilesUtils;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.services.rest.DownloadedFile;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class AddImagesWorker extends PdfImageWorker {

    public AddImagesWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        super(events, files, appLogger, request);
    }

    @Override
    public void run() {
        Json data = request.getJsonParams();
        String requestId = request.getFunctionId();
        String fileId = data.string("fileId");
        Json res = Json.map();
        try {
            InputStream is = files.download(fileId).file();
            PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(is));
            Json settings = data.json("settings");
            if (settings.contains("images")) {
                List<Json> settingsImages = settings.jsons("images");
                for (Json image : settingsImages) {
                    if (image.contains("pageIndex") && image.contains("fileId")) {
                        int pageIndex = image.integer("pageIndex");
                        if (pageIndex < pdf.getNumberOfPages()) {
                            String imageId = image.string("fileId");
                            PDPage page = pdf.getPage(pageIndex);
                            DownloadedFile downloadedFile = files.download(imageId);
                            InputStream imageIs = downloadedFile.file();
                            Json imageMetadata = files.metadata(imageId);
                            String extension = ".jpg";
                            if (imageMetadata.contains("contentType") && imageMetadata.string("contentType").equals("image/png")) {
                                extension = ".png";
                            }
                            File img = File.createTempFile("pdf-img-" + UUID.randomUUID(), extension);
                            copyInputStreamToFile(imageIs, img);
                            PDImageXObject pdImage = PDImageXObject.createFromFileByContent(img, pdf);
                            PDPageContentStream contentStream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true);
                            if (image.contains("fullPage") && image.bool("fullPage")) {
                                PDRectangle mediaBox = page.getMediaBox();
                                float pageWidth = mediaBox.getWidth();
                                float pageHeight = mediaBox.getHeight();
                                // calculate the scaling factor to make the image take up the entire page space
                                float scaleX = pageWidth / pdImage.getWidth();
                                float scaleY = pageHeight / pdImage.getHeight();
                                float scale = Math.max(scaleX, scaleY);
                                // calculate the position of the image in the top left corner of the page
                                float x = 0;
                                float y = pageHeight - (pdImage.getHeight() * scale);
                                // transformation to flip the image vertically, so it looks good
                                Matrix mt = new Matrix(1f, 0f, 0f, -1f, page.getCropBox().getLowerLeftX(), page.getCropBox().getUpperRightY());
                                contentStream.transform(mt);
                                // create a new content stream and draw the image
                                contentStream.drawImage(pdImage, x, y, pdImage.getWidth() * scale, pdImage.getHeight() * scale);
                            } else {
                                int x = image.contains("x") ? image.integer("x") : 20;
                                int y = image.contains("y") ? image.integer("y") : 20;
                                int width = image.contains("width") ? image.integer("width") : 100;
                                int height = image.contains("height") ? image.integer("height") : 100;
                                contentStream.drawImage(pdImage, x, y, width, height);
                            }
                            contentStream.close();
                        }
                    }
                }
            }
            String fileName = PdfFilesUtils.getFileName("pdf", settings);
            File temp = File.createTempFile(fileName, ".pdf");
            pdf.save(temp);
            pdf.close();
            Json fileJson = files.upload(fileName, new FileInputStream(temp), "application/pdf");
            res.set("status", "ok");
            res.set("file", fileJson);
            events.send("pdfResponse", res, requestId);
        } catch (IOException e) {
            appLogger.info("Can not generate PDF, I/O exception", e);
            res.set("status", "error");
            res.set("message", "Failed to create file");
            events.send("pdfResponse", res, requestId);
        }
    }
}
