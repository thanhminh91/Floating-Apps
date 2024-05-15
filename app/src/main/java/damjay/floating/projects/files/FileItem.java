package damjay.floating.projects.files;

import android.view.View;
import android.widget.TextView;
import java.io.File;
import android.widget.ImageView;

public class FileItem {
    private File file;

    private String fileName;
    private long fileSize;
    private boolean isDirectory;

    private String formattedSize;

    private ViewLayout layout;

    private FileItem(String fileName, long fileSize, boolean isDirectory) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.isDirectory = isDirectory;
    }

    public FileItem(File file) {
        this(file.getName(), file.length(), file.isDirectory());
        this.file = file;
    }
    
    public FileItem(File file, String name) {
        this(file);
        fileName = name;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFormattedSize(String formattedSize) {
        this.formattedSize = formattedSize;
    }

    public String getFormattedSize() {
        return formattedSize;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setLayout(ViewLayout layout) {
        this.layout = layout;
    }

    public ViewLayout getLayout() {
        if (layout == null) return new ViewLayout();
        return layout;
    }

    public static class ViewLayout {
        private TextView name;
        private TextView info;
        private ImageView icon;

        public ImageView setIcon(View icon) {
            this.icon = (ImageView) icon;
            return this.icon;
        }

        public ImageView getIcon() {
            return icon;
        }

        public TextView setName(View name) {
            this.name = (TextView) name;
            return this.name;
        }

        public TextView getName() {
            return name;
        }

        public TextView setInfo(View info) {
            this.info = (TextView) info;
            return this.info;
        }

        public TextView getInfo() {
            return info;
        }

    }

}
