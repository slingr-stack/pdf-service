package io.slingr.service.pdf.workers;

import io.slingr.services.exceptions.ErrorCode;
import io.slingr.services.exceptions.ServiceException;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.utils.Json;
import io.slingr.services.ws.exchange.FunctionRequest;
import org.apache.commons.lang.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class SplitDocumentWorker extends PdfWorker {
    private final Logger logger = LoggerFactory.getLogger(SplitDocumentWorker.class);

    public SplitDocumentWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        super(events, files, appLogger, request);
    }

    @Override
    public void run() {
        Json data = request.getJsonParams();
        String fileId = data.string("fileId");
        String filePrefix = data.string("filePrefix");
        Integer interval = data.integer("interval");
        Integer startPage = data.integer("startPage");
        Integer endPage = data.integer("endPage");

        if (StringUtils.isBlank(fileId)) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "File id can not be empty.");
        } else if (interval == null || interval <= 0) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "Interval can not be empty. Should be a positive integer.");
        }

        if (startPage == null) {
            startPage = 0;
        }
        if (StringUtils.isBlank(filePrefix)) {
            filePrefix = "split-doc-";
        }

        if (startPage < 0 || (endPage != null && endPage < 0)) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "Start Page and End Page should be a positive integer.");
        } else if (endPage != null && endPage < startPage) {
            throw ServiceException.permanent(ErrorCode.ARGUMENT, "End Page should be greater than Start Page.");
        }

        try {
            List<File> documents = new ArrayList<>();
            PDFMergerUtility merger = new PDFMergerUtility();
            Splitter splitter = new Splitter();
            InputStream is = files.download(fileId).file();
            PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(is));
            List<PDDocument> splitDoc = splitter.split(pdf);
            if (!splitDoc.isEmpty()) {
                if (endPage == null) {
                    endPage = splitDoc.size();
                }
                for (int i = startPage; i < endPage; i += interval) {
                    int end = Math.min(i + interval, splitDoc.size());
                    List<PDDocument> sp = splitDoc.subList(i, end);
                    PDDocument newDocument = new PDDocument();
                    for (PDDocument page : sp) {
                        merger.appendDocument(newDocument, page);
                        page.close();
                    }
                    int number = i / interval;
                    File temp = File.createTempFile(filePrefix + number, ".pdf");
                    newDocument.save(temp);
                    newDocument.close();
                    documents.add(temp);
                }
            }
            Json splitFiles = Json.list();
            for (File doc : documents) {
                Json fileJson = files.upload(doc.getName(), new FileInputStream(doc), "application/pdf");
                splitFiles.push(fileJson);
            }
            Json res = Json.map();
            res.set("status", "ok");
            res.set("files", splitFiles);
            events.send("pdfResponse", res, request.getFunctionId());
        } catch (IOException e) {
            logger.info(String.format("Error to load file id [%s]. Error: %s",fileId, e.getMessage()));
        }
    }
}
