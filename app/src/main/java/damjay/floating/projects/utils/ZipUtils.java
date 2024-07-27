package damjay.floating.projects.utils;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

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
}