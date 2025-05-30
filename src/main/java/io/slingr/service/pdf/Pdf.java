package io.slingr.service.pdf;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.slingr.service.pdf.processors.PdfEngine;
import io.slingr.service.pdf.processors.PdfFilesUtils;
import io.slingr.service.pdf.processors.PdfHeaderFooterHandler;
import io.slingr.service.pdf.processors.QueuePdf;
import io.slingr.service.pdf.workers.*;
import io.slingr.services.Service;
import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.framework.annotations.*;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.rest.DownloadedFile;
import io.slingr.services.services.rest.RestClient;
import io.slingr.services.utils.FilesUtils;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SlingrService(name = "pdf")
public class Pdf extends Service {
    private static final String SERVICE_NAME = "pdf";
    private final Logger logger = LoggerFactory.getLogger(Pdf.class);

    @ApplicationLogger
    protected AppLogs appLogs;

    @ServiceConfiguration
    private Json properties;

    @ServiceProperty
    private String maxThreadPool;

    @ServiceProperty
    private boolean downloadImages;

    protected ExecutorService executorService;

    public void serviceStarted() {
        logger.info(String.format("Initializing service [%s]", SERVICE_NAME));
        appLogs.info(String.format("Initializing service [%s]", SERVICE_NAME));
        int maxThread;
        if (maxThreadPool==null) maxThread = 3;
        else  maxThread = Integer.parseInt(maxThreadPool);
        PdfHeaderFooterHandler.downloadImages = this.downloadImages;
        PdfEngine.downloadImages = this.downloadImages;
        this.executorService = Executors.newFixedThreadPool(maxThread);
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
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(() -> {
            while (QueuePdf.getStreamInstance().getTotalSize() > 0) {
                createPdf(QueuePdf.getStreamInstance().poll());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        logger.debug(String.format("Properties [%s] for service [%s]", properties.toPrettyString(), SERVICE_NAME));
        logger.info(String.format("Configured service [%s]: maxThreadPool - [%s], forceDownloadImages - [%b]", SERVICE_NAME,  maxThreadPool, downloadImages));
    }

    private void createPdf(FunctionRequest req) {
        createPdf(req, true);
    }

    private void createPdf(FunctionRequest req, boolean retry) {
        logger.info("Creating pdf file");
        Json res = Json.map();
        InputStream is;
        try {
            Json data = req.getJsonParams();
            String template = data.string("tpl");
            Json settings = data.json("settings");
            PdfEngine pdfEngine = new PdfEngine(template, settings, downloadImages);
            is = pdfEngine.getPDF();
            if (is != null) {
                try {
                    logger.info("Uploading file to platform");
                    Json fileJson = files().upload(pdfEngine.getFileName(), is, "application/pdf");
                    logger.info("Done uploading file to platform");
                    res.set("status", "ok");
                    res.set("file", fileJson);
                } catch (Exception e) {
                    if (retry) {
                        logger.info("Retrying uploading");
                        createPdf(req, false);
                    } else {
                        logger.error("Problems uploading file to platform", e);
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

    @ServiceFunction(name = "generatePdf")
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
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
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
                logger.info(String.format("Html with images base64: [%s]", swString));
            }
            data.set("tpl", swString);
            QueuePdf.getStreamInstance().add(request);
            resp.set("status", "ok");
        } catch (IOException e) {
            logger.error("Can not generate pdf, i/o exception", e);
            throw ServiceException.permanent(ErrorCode.GENERAL, "Failed to create file", e);
        } catch (TemplateException e) {
            logger.warn("Can not generate pdf, template exception", e);
            appLogs.warn(String.format("Can not generate pdf, template exception [%s]", e.getMessage()));
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
    public static Map<String, String> extractImageUrlsFromHtml(String html) {
        Map<String, String> imageUrls = new LinkedHashMap<>();
        Document document = Jsoup.parse(html);
        Elements imgElements = document.select("img");
        for (Element imgElement : imgElements) {
            String url = imgElement.attr("src");
            String base64Image = convertImageToBase64(url);
            String imageType = URLConnection.guessContentTypeFromName(downloadImageToTmp(url).getName());
            String dataUrl = "data:" + imageType + ";base64, " + base64Image;
            //imageUrls.put(url, "file:///" + downloadImageToTmp(url));
            imageUrls.put(url, dataUrl);
        }
        return imageUrls;
    }

    private static String convertImageToBase64(String imageUrl) {
        RestClient restClient = RestClient.builder(imageUrl);
        DownloadedFile file = restClient.download();
        byte[] fileContent;
        try {
            fileContent = file.file().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Base64.getEncoder().encodeToString(fileContent);
    }

    /**
     * Downloads an image from the provided URL to a temporary location.
     *
     * @param imageUrl The URL of the image to download.
     * @return The local file.
     */
    private static File downloadImageToTmp(String imageUrl) {
        RestClient restClient = RestClient.builder(imageUrl);
        DownloadedFile file = restClient.download();
        return FilesUtils.copyInputStreamToTemporaryFile("", file.file());
    }

    @ServiceFunction(name = "mergeDocuments")
    public Json mergeDocuments(FunctionRequest request) {
        logger.info(String.format("Merging documents from service [%s]", SERVICE_NAME));
        MergeDocumentsWorker worker = new MergeDocumentsWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    @ServiceFunction(name = "splitDocument")
    public Json splitDocument(FunctionRequest request) {
        logger.info(String.format("Splitting documents from service [%s]", SERVICE_NAME));
        SplitDocumentWorker worker = new SplitDocumentWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    @ServiceFunction(name = "replaceHeaderAndFooter")
    public Json replaceHeaderAndFooter(FunctionRequest request) {
        logger.info(String.format("Replacing headers and footers from service [%s]", SERVICE_NAME));
        ReplaceHeaderAndFooterWorker worker = new ReplaceHeaderAndFooterWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map().set("status", "ok");
    }

    @ServiceFunction(name = "fillForm")
    public Json fillForm(FunctionRequest request) {
        logger.info(String.format("Filling forms from service [%s]", SERVICE_NAME));
        FillFormWorker worker = new FillFormWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "replaceImages")
    public Json replaceImages(FunctionRequest request) {
        logger.info(String.format("Replacing images from service [%s]", SERVICE_NAME));
        ReplaceImagesWorker worker = new ReplaceImagesWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "addImages")
    public Json addImages(FunctionRequest request) {
        logger.info(String.format("Adding images from service [%s]", SERVICE_NAME));
        AddImagesWorker worker = new AddImagesWorker(events(), files(), appLogs, request);
        this.executorService.submit(worker);
        return Json.map();
    }

    @ServiceFunction(name = "convertPdfToImages")
    public Json convertPdfToImages(FunctionRequest request) {
        logger.info(String.format("Converting pdf to images from service [%s]", SERVICE_NAME));
        Json resp = Json.map();
        Json data = request.getJsonParams();
        Json settings = data.json("settings");
        List<Object> fileIds = data.json("fileIds").toList();
        Integer dpi;
        if (data.isEmpty("dpi")) {
            dpi = 150;
        } else {
            dpi = data.integer("dpi");
        }
        if (dpi > 600) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "DPI cannot be greater than 600.");
        }
        String format;
        if (data.isEmpty("format")) {
            format = "JPEG";
        } else {
            format = data.string("format");
        }
        Executors.newSingleThreadScheduledExecutor().execute(() -> {
            logger.info("Executing function in a separated thread");
            Json convertedImages = Json.map();
            for (Object pdfId : fileIds.toArray()) {
                DownloadedFile file = files().download(pdfId.toString());
                List<String> ids = new ArrayList<>();
                try {
                    logger.info("Converting pdf to images");
                    PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(file.file()));
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    for (int page = 0; page < document.getNumberOfPages(); ++page) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                        File tempFile = File.createTempFile("image-pdf", "."+format.toLowerCase());
                        ImageIO.write(bim, format, tempFile);
                        FileInputStream in = new FileInputStream(tempFile);
                        String fileName = tempFile.getName();
                        Json response = files().upload(fileName, in, "image/"+format.toLowerCase());
                        ids.add(response.string("fileId"));
                        in.close();
                        tempFile.delete();
                    }
                    logger.info("Pdf converted successfully to images");
                    convertedImages.set(pdfId.toString(), ids);
                    document.close();
                } catch (IOException e) {
                    appLogs.error("Can not convert PDF, I/O exception", e);
                    logger.error("Can not convert pdf, i/o exception", e);
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

    @ServiceFunction(name = "convertPdfToImagesSync")
    public Json convertPdfToImagesSync(FunctionRequest request) {
        logger.info(String.format("Converting pdf to images sync from service [%s]", SERVICE_NAME));
        Json resp = Json.map();
        Json data = request.getJsonParams();
        Json settings = data.json("settings");
        List<Object> fileIds = data.json("fileIds").toList();
        Integer dpi;
        if (data.isEmpty("dpi")) {
            dpi = 150;
        } else {
            dpi = data.integer("dpi");
        }
        if (dpi > 600) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "DPI cannot be greater than 600.");
        }
        String format;
        if (data.isEmpty("format")) {
            format = "JPEG";
        } else {
            format = data.string("format");
        }
        Json convertedImages = Json.map();
        for (Object pdfId : fileIds.toArray()) {
            DownloadedFile file = files().download(pdfId.toString());
            List<String> ids = new ArrayList<>();
            try {
                logger.info("Converting pdf to images");
                PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(file.file()));
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                for (int page = 0; page < document.getNumberOfPages(); ++page) {
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                    File tempFile = File.createTempFile("image-pdf", "."+format.toLowerCase());
                    ImageIO.write(bim, format, tempFile);
                    FileInputStream in = new FileInputStream(tempFile);
                    String fileName = tempFile.getName();
                    Json response = files().upload(fileName, in, "image/"+format.toLowerCase());
                    ids.add(response.string("fileId"));
                    in.close();
                    tempFile.delete();
                }
                logger.info("Pdf converted successfully to images");
                convertedImages.set(pdfId.toString(), ids);
                document.close();
            } catch (IOException e) {
                appLogs.error("Can not convert PDF, I/O exception", e);
                logger.error("Can not convert pdf, i/o exception", e);
                throw ServiceException.permanent(ErrorCode.GENERAL, "Failed to convert pdf to images", e);
            }
        }
        resp.set("status", "ok");
        resp.set("imagesIds", convertedImages);
        resp.set("config", settings);
        return resp;
    }

    @ServiceFunction(name = "convertPdfToText")
    public Json convertPdfToText (FunctionRequest request){
        logger.info(String.format("Converting pdf to text from service [%s]", SERVICE_NAME));
        Json data = request.getJsonParams();
        String fileId = data.string("fileId");
        Json resp = Json.map();
        InputStream downloadedFile = null; 
        PDDocument pdf = null;
        try {
            try {
                downloadedFile = files().download(fileId).file();
                if (downloadedFile == null) {
                    appLogs.error("Cannot convert PDF, empty file.");
                    logger.error("Cannot convert pdf, empty file.");
                    resp.set("status", "failed");
                    resp.set("message", "File does not exist or is empty.");
                    return resp;
                }
            } catch (Exception e) {
                appLogs.error("Cannot find the file on the server.", e);
                logger.error("File not found.", e);
                resp.set("status", "failed");
                resp.set("message", "File does not exist on the application.");
                return resp;
            }

            try {
                pdf = Loader.loadPDF(new RandomAccessReadBuffer(downloadedFile));
                if (!pdf.isEncrypted()) {
                    PDFTextStripper pdfStripper = new PDFTextStripper();
                    String text = pdfStripper.getText(pdf);
                    resp.set("pdfText", text);
                    resp.set("status", "ok");
                } else {
                    appLogs.error("Cannot convert PDF, file is encrypted.");
                    logger.error("Cannot convert pdf, file is encrypted.");
                    resp.set("status", "failed");
                    resp.set("message", "File is encrypted.");
                    return resp;
                }
            } catch (IOException e) {
                appLogs.error("Cannot convert PDF, I/O exception", e);
                logger.error("Cannot convert pdf, I/O exception", e);
                resp.set("status", "failed");
                resp.set("message", "Cannot convert pdf. An error occurred during the conversion process.");
                return resp;
            }
        } finally {
            if (pdf != null) {
                try {
                    pdf.close();
                } catch (IOException e) {
                    appLogs.error("Error closing PDF document.", e);
                    logger.error("Error closing PDF document.", e);
                }
            }
            if (downloadedFile != null) {
                try {
                    downloadedFile.close();
                } catch (IOException e) {
                    appLogs.error("Error closing downloaded file stream.", e);
                    logger.error("Error closing downloaded file stream.", e);
                }
            }
        }
        events().send("pdfResponse", resp, request.getFunctionId());
        return Json.map().set("status", "ok").set("message", "PDF conversion completed.");
    }
}
