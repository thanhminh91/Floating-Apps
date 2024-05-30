package damjay.floating.projects.customadapters;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import androidx.core.content.res.ResourcesCompat;
import damjay.floating.projects.R;
import damjay.floating.projects.files.FileItem;
import damjay.floating.projects.utils.FormatUtils;
import java.io.File;
import java.util.ArrayList;

public class FileListAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<FileItem> fileItems = new ArrayList<>();
    private ArrayList<FileItem> internalStorageDrives;
    public File folder;

    public File internalDrive;

    public FileListAdapter(Context context, File folder) {
        this.context = context;
        getStorageDrives();
        if (folder != null) updatePath(folder);
    }

    private void getStorageDrives() {
        try {
            if (internalDrive == null) 
                internalDrive = Environment.getExternalStorageDirectory();
            if (internalStorageDrives == null) {
                internalStorageDrives = new ArrayList<>();
                internalStorageDrives.add(new FileItem(internalDrive, "Phone Storage"));
            }
            folder = null;
            fileItems.clear();
            for (FileItem item : internalStorageDrives) {
                fileItems.add(item);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void updatePath(File folder) {
        String rootValue = internalDrive.getParentFile().getParent();
        if (folder.getPath().equals(rootValue)) {
            if (this.folder != null) {
                // Is this a storage drive or internal memory?
                if (this.folder.getPath().equals(internalDrive.getParent())) return;
                else {
                    for (FileItem file : internalStorageDrives) {
                        if (file.getFile().getPath().equals(this.folder.getPath())) {
                            // If the drive has been added before:
                            // Reload the storage drives
                            getStorageDrives();
                            return;
                        }
                    }
                    // The drive has not yet been added.
                    internalStorageDrives.add(new FileItem(this.folder, "SDCard" + internalStorageDrives.size()));
                    // Reload the storage drives
                    getStorageDrives();
                }
            }
            return;
        }
        fileItems.clear();
        rootValue = internalDrive.getParent();
        if (folder.getPath().equals(rootValue)) {
            getStorageDrives();
            return;
        }
        
        int folders = 0;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory())
                fileItems.add(folders++, new FileItem(file));
            else
                fileItems.add(new FileItem(file));
        }
        this.folder = folder;
    }

    @Override
    public int getCount() {
        return fileItems == null ? 0 : fileItems.size();
    }

    @Override
    public Object getItem(int index) {
        return fileItems.size() <= index ? null : fileItems.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (fileItems == null) return view;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.file_items, viewGroup, false);
        }
        FileItem item = fileItems.get(position);
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(view.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(view.findViewById(R.id.fileInfo)).setText((item.isDirectory() ? "" : FormatUtils.formatSize(item.getFileSize()) + ", ") + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(view.findViewById(R.id.file_icon));
        int resource = item.isDirectory() ? R.drawable.folder : R.drawable.file_icon;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), resource, context.getTheme()));
        }
        return view;
    }

}
