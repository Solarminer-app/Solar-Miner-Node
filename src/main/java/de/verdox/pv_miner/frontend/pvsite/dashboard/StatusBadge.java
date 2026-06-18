package de.verdox.pv_miner.frontend.pvsite.dashboard;

import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.FrontendColor;

public class StatusBadge extends TranslatableSpan {
    public StatusBadge(String status) {
        super("");
        setText("status." + status.toLowerCase());
        getStyle()
                .set("padding", "3px 8px")
                .set("border-radius", "12px")
                .set("font-size", "12px")
                .set("font-weight", "bold");

        if (status.equalsIgnoreCase("Online") || status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Running") || status.equalsIgnoreCase("MINING")) {
            getStyle().set("background-color", "rgba(46, 204, 113, 0.2)").set("color", "#2ecc71");
        } else if (status.equalsIgnoreCase("Offline") || status.equalsIgnoreCase("Stopped") || status.equalsIgnoreCase("PAUSED")) {
            getStyle().set("background-color", "rgba(231, 76, 60, 0.2)").set("color", "#e74c3c");
        } else {
            getStyle().set("background-color", "rgba(241, 196, 15, 0.2)").set("color", FrontendColor.TEXT_VALUE_YELLOW);
        }
    }
}
