package damjay.floating.projects.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class IOUtils {

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    public static boolean safeCopy(Context context, Uri uri, File file) {
        try {
            copy(context, uri, file);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }
    
    public static void copy(Context context, Uri uri, File file) throws IOException {
        InputStream src = context.getContentResolver().openInputStream(uri);
        OutputStream dest = new FileOutputStream(file);
        copy(src, dest);
    }
    
    public static void copy(InputStream src, OutputStream dest) throws IOException {
        int read;
        byte[] buffer = new byte[100 * 1024];
        while ((read = src.read(buffer)) > 0) {
            dest.write(buffer, 0, read);
        }
        src.close();
        dest.close();
    }
}
