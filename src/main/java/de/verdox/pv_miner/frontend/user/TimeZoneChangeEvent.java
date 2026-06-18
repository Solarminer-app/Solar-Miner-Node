package de.verdox.pv_miner.frontend.user;

import com.vaadin.flow.component.UI;

import java.time.ZoneId;
import java.util.EventObject;

public class TimeZoneChangeEvent extends EventObject {
    private final ZoneId zoneId;

    /**
     * Timezone change event constructor.
     *
     * @param ui       The ui on which the Event initially occurred.
     * @param zoneId new zoneId that was set
     */
    public TimeZoneChangeEvent(UI ui, ZoneId zoneId) {
        super(ui);
        this.zoneId = zoneId;
    }

    /**
     * Get the new timezone that was set.
     *
     * @return set zoneId
     */
    public ZoneId getZoneId() {
        return zoneId;
    }

    /**
     * Returns the UI where the timezone changed in.
     *
     * @return the ui
     */
    public UI getUI() {
        return (UI) getSource();
    }
}
