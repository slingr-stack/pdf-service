package io.slingr.service.pdf.workers;

import io.slingr.service.pdf.PdfFillForm;
import io.slingr.services.services.AppLogs;
import io.slingr.services.services.Events;
import io.slingr.services.services.Files;
import io.slingr.services.ws.exchange.FunctionRequest;

public abstract class PdfWorker implements Runnable {
    protected Events events;
    protected Files files;
    protected PdfFillForm pdfFillForm;
    protected AppLogs appLogger;
    protected FunctionRequest request;

    PdfWorker(Events events, Files files, AppLogs appLogger, FunctionRequest request) {
        this.events = events;
        this.files = files;
        this.appLogger = appLogger;
        this.request = request;
    }
}
