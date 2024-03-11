package io.slingr.service.pdf.processors;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.slingr.service.pdf.Pdf;
import io.slingr.services.utils.Json;
import io.slingr.services.utils.Strings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class PdfEngine {

    private final Logger logger = LoggerFactory.getLogger(PdfEngine.class);

    private final String template;
    List<String> commandParams;
    private String sourceTmpFile;
    private String targetTmpFile;
    private final String fileName;

    private final String headerTmpFile;
    private final String footerTmpFile;
    public static boolean downloadImages;


    public PdfEngine(String tpl, Json settings, boolean downloadImages) throws IOException, TemplateException {

        template = tpl;
        commandParams = new ArrayList<>();
        commandParams.add("/usr/bin/wkhtmltopdf");

        if (settings == null) {
            settings = Json.map();
        }

        this.headerTmpFile = this.addFileCommandParams(commandParams, settings.string("headerTemplate"), settings.json("headerData"), "--header-html");
        this.footerTmpFile = this.addFileCommandParams(commandParams, settings.string("footerTemplate"), settings.json("footerData"), "--footer-html");

        String pageSize = settings.string("pageSize");
        if (StringUtils.isBlank(pageSize)) {
            pageSize = "A4";
        }
        commandParams.add("--page-size");
        commandParams.add(pageSize);

        if (downloadImages){
            commandParams.add("--enable-local-file-access");
        }

        String orientation = settings.string("orientation");
        if (StringUtils.isNotBlank(orientation) && orientation.equalsIgnoreCase("landscape")) {
            commandParams.add("--orientation");
            commandParams.add("Landscape");
        }

        Integer marginBottom = settings.integer("marginBottom");
        if (marginBottom != null) {
            commandParams.add("--margin-bottom");
            commandParams.add(Integer.toString(marginBottom));
        }

        Integer marginLeft = settings.integer("marginLeft");
        if (marginLeft != null) {
            commandParams.add("--margin-left");
            commandParams.add(Integer.toString(marginLeft));
        }

        Integer marginRight = settings.integer("marginRight");
        if (marginRight != null) {
            commandParams.add("--margin-right");
            commandParams.add(Integer.toString(marginRight));
        }

        Integer marginTop = settings.integer("marginTop");
        if (marginTop != null) {
            commandParams.add("--margin-top");
            commandParams.add(Integer.toString(marginTop));
        }

        String fn = settings.string("name");
        if (StringUtils.isBlank(fn)) {
            fileName = "pdf-" + new Date().getTime();
        } else {
            fileName = fn;
        }
    }

    private String addFileCommandParams(List<String> commandParams, String template, Json data, String command) throws IOException, TemplateException {
        String path = null;
        if (StringUtils.isNotBlank(template)) {
            Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
            if (data != null) {
                Template tpl = new Template("name", new StringReader(template), cfg);
                tpl.setAutoFlush(true);
                StringWriter sw = new StringWriter();
                tpl.process(data.toMap(), sw);
                template = sw.toString();
                if (downloadImages) {
                    Map<String, String> urlImgs = Pdf.extractImageUrlsFromHtml(template);
                    for (Map.Entry<String, String> entry : urlImgs.entrySet()) {
                        template = template.replace(entry.getKey(), entry.getValue());
                    }
                    logger.info(String.format("Template [%s]", template));
                }
                File temp = File.createTempFile("pdf-header-" + Strings.randomUUIDString(), ".html");
                FileUtils.writeStringToFile(temp, template, "UTF-8");
                path = temp.getAbsolutePath();
                commandParams.add(command);
                commandParams.add(path);
            }
        }
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public InputStream getPDF() {
        try {
            File temp = File.createTempFile("pdf-wkhtmltopdf-" + Strings.randomUUIDString(), ".html");
            FileUtils.writeStringToFile(temp, template, "UTF-8");
            sourceTmpFile = temp.getAbsolutePath();
            targetTmpFile = temp.getAbsolutePath().replaceAll("\\.html", ".pdf");
            commandParams.add(sourceTmpFile);
            commandParams.add(targetTmpFile);
        } catch (IOException e) {
            logger.error("Error creating pdf temporal files", e);
            return null;
        }
        return PdfHeaderFooterHandler.openStream(commandParams, targetTmpFile);
    }

    public void cleanTmpFiles() {
        boolean deleteF = false, deleteS = false, deleteT = false, deleteH = false;
        if (sourceTmpFile != null) {
            deleteS = (new File(sourceTmpFile)).delete();
        }
        if (targetTmpFile != null) {
            deleteT = (new File(targetTmpFile)).delete();
        }
        if (headerTmpFile != null) {
            deleteH = (new File(headerTmpFile)).delete();
        }
        if (footerTmpFile != null) {
            deleteF = (new File(footerTmpFile)).delete();
        }
        if (deleteH && deleteT && deleteF && deleteS) {
            logger.info("Cleaning temporal files");
        }

        String TMP_PATH = "/tmp";
        File tmpFolder = new File(TMP_PATH);
        if (tmpFolder.exists() && tmpFolder.isDirectory()) {
            FilenameFilter filter = (dir, name) -> name.endsWith(".tmp");
            File[] tmpFiles = tmpFolder.listFiles(filter);
            if (tmpFiles != null) {
                for (File tmpFile : tmpFiles) {
                    if (tmpFile.delete())  logger.info("Deleted: " + tmpFile.getName());
                }
            }
            logger.info(".tmp files deleted from the /tmp folder.");
        } else {
            logger.info("/tmp folder does not exist or is not a directory.");
        }
    }
}
