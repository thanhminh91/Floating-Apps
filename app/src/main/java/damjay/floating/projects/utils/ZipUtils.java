package damjay.floating.projects.utils;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.BufferedOutputStream;
import java.util.zip.ZipInputStream;

public class ZipUtils {

    public static boolean extractZip(File file, File outputDir) {
        try(ZipFile zipFile = new ZipFile(file)) {
            Enumeration<ZipEntry> zipEntries = (Enumeration<ZipEntry>) zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry curEntry = zipEntries.nextElement();
                String path = curEntry.getName();
                File outputFile = new File(outputDir, path);
                if (curEntry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();
                    InputStream stream = zipFile.getInputStream(curEntry);
                    if (!FileUtils.copyStream(stream, new FileOutputStream(outputFile))) return false;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }

    public void unzipFile(String zipFilePath, String destDirectory) {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        ZipInputStream zipIn = null;
        try {
            zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
            ZipEntry entry = zipIn.getNextEntry();
            // iterates over entries in the zip file
            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    // if the entry is a file, extracts it
                    extractFile(zipIn, filePath);
                } else {
                    // if the entry is a directory, make the directory
                    File dir = new File(filePath);
                    dir.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (zipIn != null) {
                    zipIn.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }
}