package io.slingr.service.pdf;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.slingr.service.pdf.workers.*;
import io.slingr.services.Service;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.framework.annotations.ApplicationLogger;
import io.slingr.services.framework.annotations.ServiceFunction;
import io.slingr.services.framework.annotations.ServiceProperty;
import io.slingr.services.framework.annotations.SlingrService;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.rest.DownloadedFile;
import io.slingr.services.services.rest.RestClient;
import io.slingr.services.utils.FilesUtils;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@SlingrService(name = "pdf", functionPrefix = "_")
public class Pdf extends Service {

    private Logger logger = LoggerFactory.getLogger(Pdf.class);

    @ApplicationLogger
    protected AppLogs appLogger;

    @ServiceProperty
    private String maxThreadPool;

    @ServiceProperty
    private boolean downloadImages;

    private final int MAX_THREADS_POOL = 3;

    protected ExecutorService executorService;

    public void serviceStarted() {

        int maxTreads = MAX_THREADS_POOL;
        try {
            maxTreads = Integer.valueOf(maxThreadPool);
        } catch (Exception ex) {
        }

        this.executorService = Executors.newFixedThreadPool(maxTreads);

        if (!properties().isLocalDeployment()) {
            try {
                PdfFilesUtils pdfFilesUtils = new PdfFilesUtils();
                pdfFilesUtils.executeCommands();
                pdfFilesUtils.exportResource("wkhtmltopdf");
                pdfFilesUtils.exportResource("wkhtmltoimage");
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }


        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            while (true) {
                generateAutoPdf();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    logger.info("Generate pdf thread was interrupted.");
                }
            }
        });

    }

    @ServiceFunction(name = "_generatePdf")
    public Json generatePdf(FunctionRequest request) {
        logger.info("Creating pdf from template");

        Json data = request.getJsonParams();
        Json resp = Json.map();
        String template = data.string("template");
        if (StringUtils.isBlank(template)) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "Template can not be empty.");
        }
        Json jData = data.json("data");
        if (jData == null) {
            jData = Json.map();
        }


        Configuration cfg = new Configuration();
        Template tpl;
        StringWriter sw = null;
        try {
            tpl = new Template("name", new StringReader(template), cfg);
            tpl.setAutoFlush(true);
            sw = new StringWriter();
            tpl.process(jData.toMap(), sw);
            String swString = sw.toString();
            if (downloadImages) {
                Map<String, String> urlImgs = extractImageUrlsFromHtml(swString);
                for (Map.Entry<String, String> entry : urlImgs.entrySet()) {
                    swString = swString.replace(entry.getKey(), entry.getValue());
                }
            }
            data.set("tpl", swString);
            QueuePdf.getStreamInstance().add(request);
            resp.set("status", "ok");
        } catch (IOException e) {
            logger.error("Can not generate PDF, I/O exception", e);
            throw ServiceException.permanent(ErrorCode.GENERAL, "Failed to create file", e);
        } catch (TemplateException e) {
            logger.error("Can not generate PDF, template exception", e);
            throw ServiceException.permanent(ErrorCode.GENERAL, "Failed to parse template", e);
        } finally {
            try {
                if (sw != null) {
                    sw.flush();
                    sw.close();
                }
            } catch (IOException ioe) {
                logger.info("String writer can not be closed.");
            }
        }

        return resp;
    }

    /**
     * Extracts image URLs from the provided HTML content.
     *
     * @param html The HTML content from which to extract image URLs.
     * @return A map containing the original image URLs as keys and their local paths as values.
     */
    private Map<String, String> extractImageUrlsFromHtml(String html) {
        Map<String, String> imageUrls = new LinkedHashMap<>();
        Document document = Jsoup.parse(html);
        Elements imgElements = document.select("img");
        for (Element imgElement : imgElements) {
            String url = imgElement.attr("src");
            imageUrls.put(url, "file:///" + downloadImageToTmp(url));
        }
        return imageUrls;
    }

    /**
     * Downloads an image from the provided URL to a temporary location.
     *
     * @param imageUrl The URL of the image to download.
     * @return The local path to the downloaded image.
     */
    private String downloadImageToTmp(String imageUrl) {
        RestClient restClient = RestClient.builder(imageUrl);
        DownloadedFile file = restClient.download();
        File localFile = FilesUtils.copyInputStreamToTemporaryFile("", file.getFile());
        return localFile.getPath();
    }



    @ServiceFunction(name = "_fillForm")
    public Json fillForm(FunctionRequest request) {
        FillFormWorker worker = new FillFormWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "_mergeDocuments")
    public Json mergeDocuments(FunctionRequest request) {
        MergeDocumentsWorker worker = new MergeDocumentsWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    @ServiceFunction(name = "_splitDocument")
    public Json splitDocument(FunctionRequest request) {
        SplitDocumentWorker worker = new SplitDocumentWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    @ServiceFunction(name = "_replaceHeaderAndFooter")
    public Json replaceHeaderAndFooter(FunctionRequest request) {
        ReplaceHeaderAndFooterWorker worker = new ReplaceHeaderAndFooterWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    private void createPdf(FunctionRequest req) {
        createPdf(req, true);
    }

    private void createPdf(FunctionRequest req, boolean retry) {
        logger.info("Creating pdf file");
        Json res = Json.map();
        InputStream is = null;
        try {
            Json data = req.getJsonParams();
            String template = data.string("tpl");
            Json settings = data.json("settings");
            PdfEngine pdfEngine = new PdfEngine(template, settings, downloadImages);
            is = pdfEngine.getPDF();
            if (is != null) {
                try {
                    logger.info("Uploading file to endpoint services");
                    Json fileJson = files().upload(pdfEngine.getFileName(), is, "application/pdf");
                    logger.info("Done uploading file to endpoint services");
                    res.set("status", "ok");
                    res.set("file", fileJson);
                } catch (Exception e) {
                    logger.error("Problems uploading file to endpoint services", e);
                    if (retry) {
                        logger.info("Retrying uploading");
                        createPdf(req, false);
                    }
                } finally {
                    pdfEngine.cleanTmpFiles();
                    try {
                        is.close();
                    } catch (IOException io) {
                        logger.info("Can not close stream", io);
                    }
                }
            } else {
                logger.warn("PDF file can not be generated");
                res.set("status", "error");
                res.set("message", ServiceException.json(ErrorCode.GENERAL, "PDF file was not generated."));
            }
        } catch (Exception ex) {
            logger.info("Failed to generate PDF", ex);
            res.set("status", "error");
            res.set("message", ServiceException.json(ErrorCode.GENERAL, "Failed to generate PDF: " + ex.getMessage(), ex));
        }
        logger.info("Pdf has been successfully created. Sending [pdfResponse] event to the app");
        events().send("pdfResponse", res, req.getFunctionId());
        logger.info("Done sending [pdfResponse] event to the app");
    }

    @ServiceFunction(name = "_replaceImages")
    public Json replaceImages(FunctionRequest request) {
        ReplaceImagesWorker worker = new ReplaceImagesWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "_addImages")
    public Json addImages(FunctionRequest request) {
        AddImagesWorker worker = new AddImagesWorker(events(), files(), appLogger, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "_convertPdfToImages")
    public Json convertPdfToImages(FunctionRequest request) throws IOException {
        Json resp = Json.map();
        Json data = request.getJsonParams();
        Json settings = data.json("settings");
        List<Object> fileIds = data.json("fileIds").toList();
        Integer dpi = data.integer("dpi");
        if (dpi > 600) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "DPI cannot be greater than 600.");
        }
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            logger.info("Executing function in a separated thread");
            Json convertedImages = Json.map();
            for (Object pdfId : fileIds.toArray()) {
                DownloadedFile file = files().download(pdfId.toString());
                List<String> ids = new ArrayList<>();
                try {
                    logger.info("Converting PDF to images");
                    PDDocument document = PDDocument.load(file.getFile());
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    for (int page = 0; page < document.getNumberOfPages(); ++page) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                        File tempFile = File.createTempFile("image-pdf", ".jpeg");
                        ImageIO.write(bim, "JPEG", tempFile);
                        FileInputStream in = new FileInputStream(tempFile);
                        String fileName = tempFile.getName();
                        Json response = files().upload(fileName, in, "image/jpeg");
                        ids.add(response.string("fileId"));
                        in.close();
                        tempFile.delete();
                    }
                    logger.info("PDF converted successfully to images");
                    convertedImages.set(pdfId.toString(), ids);
                    document.close();
                } catch (IOException e) {
                    appLogger.error("Can not convert PDF, I/O exception", e);
                    logger.error("Can not convert PDF, I/O exception", e);
                    throw ServiceException.permanent(ErrorCode.GENERAL, "Failed to convert pdf to images", e);
                }
            }
            resp.set("status", "ok");
            resp.set("imagesIds", convertedImages);
            resp.set("config", settings);
            events().send("pdfResponse", resp, request.getFunctionId());
        });

        return Json.map().set("status", "ok");
    }

    private final ReentrantLock pdfLock = new ReentrantLock();

    private void generateAutoPdf() {
        while (QueuePdf.getStreamInstance().getTotalSize() > 0) {
            createPdf(QueuePdf.getStreamInstance().poll());
        }
    }

}
