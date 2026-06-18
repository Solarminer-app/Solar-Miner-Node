package de.verdox.pv_miner.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FormatUtil {
    public static String formatNumber(double number) {
        return String.format("%,.2f", number);
    }

    public static String formatSmallNumber(double number) {
        return String.format("%,.10f", number);
    }

    public static String formatBitcoin(double value) {
        DecimalFormat df = new DecimalFormat("#.########", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(value);
    }

    public static String formatHashrateFromTHs(double teraHashPerSecond) {
        if (teraHashPerSecond <= 0) {
            return "0,00 H/s";
        }
        String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s", "EH/s", "ZH/s"};
        double value = teraHashPerSecond * 1_000_000_000_000.0;
        int unitIndex = 0;
        while (value >= 1000.0 && unitIndex < units.length - 1) {
            value /= 1000.0;
            unitIndex++;
        }
        return String.format(java.util.Locale.GERMAN, "%.2f %s", value, units[unitIndex]);
    }

    public static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder formattedTime = new StringBuilder();

        if (hours > 0) {
            formattedTime.append(hours).append("h");
        }
        if (minutes > 0) {
            if (!formattedTime.isEmpty()) formattedTime.append(",");
            formattedTime.append(minutes).append("m");
        }
        if(seconds > 0){
            if (!formattedTime.isEmpty()) formattedTime.append(",");
            formattedTime.append(secs).append("s");
        }

        return formattedTime.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        String[] units = {"KB", "MB", "GB", "TB", "PB", "EB"};
        int unitIndex = -1;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }
}
