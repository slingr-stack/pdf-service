package io.slingr.service.pdf.processors;

import io.slingr.service.pdf.Pdf;
import io.slingr.services.utils.Json;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.Date;

public class PdfFilesUtils {

    public void executeCommands() throws IOException, InterruptedException {
        File tempScript = createTempScript();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", tempScript.toString());
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } finally {
            System.out.println("Opening commands: " + tempScript.delete());
        }
    }

    public File createTempScript() throws IOException {
        //log to read distro
        Process p = Runtime.getRuntime().exec(new String[]{ "bash", "-c", "cat /etc/os-release" });
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while((line = reader.readLine()) != null) {
            System.out.println("[HOST OS] " + line);
        }
        reader.close();
        File tempScript = File.createTempFile("script", null);
        Writer streamWriter = new OutputStreamWriter(new FileOutputStream(tempScript));
        PrintWriter printWriter = new PrintWriter(streamWriter);
        printWriter.println("#!/bin/bash");
        printWriter.println("set -e");
        printWriter.println("apt-get update -y");
        printWriter.println("apt-get install -y xvfb libfontconfig libxrender1 wget ca-certificates");
        printWriter.println("wget -q https://archive.ubuntu.com/ubuntu/pool/main/o/openssl1.0/libssl1.0.0_1.0.2n-1ubuntu5.9_amd64.deb");
        printWriter.println("dpkg -i libssl1.0.0_1.0.2n-1ubuntu5.9_amd64.deb");
        printWriter.println("rm libssl1.0.0_1.0.2n-1ubuntu5.9_amd64.deb");
        printWriter.close();
        return tempScript;
    }

    public void exportResource(String resourceName) throws Exception {
        InputStream stream = null;
        OutputStream resStreamOut = null;
        String destFolder = "/usr/bin/";
        try {
            stream = Pdf.class.getClassLoader().getResourceAsStream(resourceName);//note that each / is a directory down in the "jar tree" been the jar the root of the tree
            if (stream == null) {
                throw new Exception("Cannot get resource \"" + resourceName + "\" from Jar file.");
            }
            int readBytes;
            byte[] buffer = new byte[4096];
            resStreamOut = new FileOutputStream(destFolder + resourceName);
            while ((readBytes = stream.read(buffer)) > 0) {
                resStreamOut.write(buffer, 0, readBytes);
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
            if (resStreamOut != null) {
                resStreamOut.close();
            }
        }
        File f = new File(destFolder + resourceName);
        if (f.setExecutable(true) && f.setReadable(true) && f.setWritable(false)) {
            System.out.println("File access provided");
        }
    }

    public static String getFileName(String prefix, Json settings) {
        String fileName = prefix + "-" + new Date().getTime();
        if (settings.contains("name") && StringUtils.isNotBlank(settings.string("name"))) {
            fileName = settings.string("name");
        }
        return fileName;
    }

}
