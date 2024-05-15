package damjay.floating.projects.utils;

import java.util.Date;
import java.math.BigDecimal;

public class FormatUtils {
    
    public static String formatSize(long length) {
        String[] suffices = {" bytes", "KB", "MB", "GB", "TB"};
        int sizeType = 0;

        while (sizeType < suffices.length && length >>> (10L * ++sizeType) != 0L);

        return approximateNumberToTwoPlaces((double) length / (double) (1 << (10 * (sizeType - 1)))) + suffices[sizeType - 1];
    }

    private static String approximateNumberToTwoPlaces(double doubleValue) {
//        long beforeDecimal = (long) doubleValue;
//        int afterDecimal = (int) ((doubleValue - beforeDecimal) * 1000); // Max value of 999
//
//        int lastDigit = afterDecimal % 10;
//        afterDecimal = afterDecimal / 10;
//
//        if (lastDigit >= 5) afterDecimal++;
//        if (afterDecimal > 99) beforeDecimal++;
//        afterDecimal %= 100;
//
//        return beforeDecimal + "." + (afterDecimal < 10 ? "0" : "") + afterDecimal;
        return Double.toString(new BigDecimal(doubleValue).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
    }

    public static String formatDate(long lastModified) {
        if (lastModified < 0) return "";
        Date date = new Date(lastModified);
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

        int hours = date.getHours();
        int minutes = date.getMinutes();

        // April 23
        // 2024
        String dateString = months[date.getMonth()] + " " + date.getDate();
        // 4:36 pm
        String timeString = (hours % 12 != 0 ? hours % 12 : 12) + ":" + (minutes < 10 ? "0" : "") + minutes + (hours >= 12 ? " pm" : " am");

        return dateString + " " + timeString;
    }
    
}
