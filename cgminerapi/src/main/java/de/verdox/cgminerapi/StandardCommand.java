package de.verdox.cgminerapi;

import de.verdox.cgminerapi.dto.CGMinerDTO;

public enum StandardCommand implements CGMinerCommand<CGMinerDTO> {
    VERSION(
            "version",
            APIVersion.of(0, 7),
            false,
            ResponseSection.VERSION,
            CGMinerDTO.Version.class
    ),

    CONFIG(
            "config",
            APIVersion.of(1, 0),
            false,
            ResponseSection.CONFIG,
            CGMinerDTO.Config.class
    ),

    SUMMARY(
            "summary",
            APIVersion.of(0, 7),
            false,
            ResponseSection.SUMMARY,
            CGMinerDTO.Summary.class
    ),

    POOLS(
            "pools",
            APIVersion.of(0, 7),
            false,
            ResponseSection.POOLS,
            CGMinerDTO.Pools.class
    ),

    DEVS(
            "devs",
            APIVersion.of(0, 7),
            false,
            ResponseSection.DEVS,
            CGMinerDTO.Devs.class
    ),

    EDEVS(
            "edevs",
            APIVersion.of(3, 3),
            false,
            ResponseSection.DEVS,
            CGMinerDTO.Devs.class
    ),

    PGA(
            "pga",
            APIVersion.of(1, 6),
            false,
            ResponseSection.PGA,
            CGMinerDTO.Pga.class
    ),

    PGA_COUNT(
            "pgacount",
            APIVersion.of(1, 6),
            false,
            ResponseSection.PGAS,
            CGMinerDTO.PgaCount.class
    ),

    ASC(
            "asc",
            APIVersion.of(1, 26),
            false,
            ResponseSection.ASC,
            CGMinerDTO.Asc.class
    ),

    ASC_COUNT(
            "asccount",
            APIVersion.of(1, 26),
            false,
            ResponseSection.ASCS,
            CGMinerDTO.AscCount.class
    ),

    NOTIFY(
            "notify",
            APIVersion.of(1, 4),
            false,
            ResponseSection.NOTIFY,
            CGMinerDTO.Notify.class
    ),

    DEVDETAILS(
            "devdetails",
            APIVersion.of(1, 8),
            false,
            ResponseSection.DEVDETAILS,
            CGMinerDTO.DevDetails.class
    ),

    STATS(
            "stats",
            APIVersion.of(1, 10),
            false,
            ResponseSection.STATS,
            CGMinerDTO.Stats.class
    ),

    ESTATS(
            "estats",
            APIVersion.of(3, 3),
            false,
            ResponseSection.STATS,
            CGMinerDTO.Stats.class
    ),

    CHECK(
            "check",
            APIVersion.of(1, 13),
            false,
            ResponseSection.CHECK,
            CGMinerDTO.Check.class
    ),

    COIN(
            "coin",
            APIVersion.of(1, 17),
            false,
            ResponseSection.COIN,
            CGMinerDTO.Coin.class
    ),

    DEBUG(
            "debug",
            APIVersion.of(1, 19),
            true,
            ResponseSection.DEBUG,
            CGMinerDTO.Debug.class
    ),

    USB_STATS(
            "usbstats",
            APIVersion.of(1, 21),
            false,
            ResponseSection.USBSTATS,
            CGMinerDTO.UsbStats.class
    ),

    LCD(
            "lcd",
            APIVersion.of(3, 4),
            false,
            ResponseSection.LCD,
            CGMinerDTO.Lcd.class
    ),

    PRIVILEGED(
            "privileged",
            APIVersion.of(1, 2),
            false,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    SWITCH_POOL(
            "switchpool",
            APIVersion.of(1, 0),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ENABLE_POOL(
            "enablepool",
            APIVersion.of(1, 2),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    DISABLE_POOL(
            "disablepool",
            APIVersion.of(1, 2),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ADD_POOL(
            "addpool",
            APIVersion.of(1, 3),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    REMOVE_POOL(
            "removepool",
            APIVersion.of(1, 7),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    POOL_PRIORITY(
            "poolpriority",
            APIVersion.of(1, 15),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    POOL_QUOTA(
            "poolquota",
            APIVersion.of(1, 30),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    SAVE(
            "save",
            APIVersion.of(1, 0),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    QUIT(
            "quit",
            APIVersion.of(0, 7),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    RESTART(
            "restart",
            APIVersion.of(1, 9),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    FAILOVER_ONLY(
            "failover-only",
            APIVersion.of(1, 16),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    SET_CONFIG(
            "setconfig",
            APIVersion.of(1, 19),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    PGA_ENABLE(
            "pgaenable",
            APIVersion.of(1, 6),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    PGA_DISABLE(
            "pgadisable",
            APIVersion.of(1, 6),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    PGA_IDENTIFY(
            "pgaidentify",
            APIVersion.of(1, 19),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    PGA_SET(
            "pgaset",
            APIVersion.of(1, 23),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ASC_ENABLE(
            "ascenable",
            APIVersion.of(1, 26),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ASC_DISABLE(
            "ascdisable",
            APIVersion.of(1, 26),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ASC_IDENTIFY(
            "ascidentify",
            APIVersion.of(1, 26),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ASC_SET(
            "ascset",
            APIVersion.of(1, 27),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    ZERO(
            "zero",
            APIVersion.of(1, 24),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    HOTPLUG(
            "hotplug",
            APIVersion.of(1, 25),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    ),

    LOCK_STATS(
            "lockstats",
            APIVersion.of(1, 31),
            true,
            ResponseSection.STATUS,
            CGMinerDTO.Status.class
    );

    private final String command;
    private final APIVersion apiVersion;
    private final boolean restrictedAccess;
    private final ResponseSection responseSection;
    private final Class<? extends CGMinerDTO> responseDTO;

    StandardCommand(String command, APIVersion apiVersion, boolean restrictedAccess, ResponseSection responseSection, Class<? extends CGMinerDTO> responseDTO) {
        this.command = command;
        this.apiVersion = apiVersion;
        this.restrictedAccess = restrictedAccess;
        this.responseSection = responseSection;
        this.responseDTO = responseDTO;
    }

    @Override
    public String command() {
        return command;
    }

    @Override
    public APIVersion minimumVersion() {
        return apiVersion;
    }

    @Override
    public boolean privileged() {
        return restrictedAccess;
    }

    @Override
    public ResponseSection responseSection() {
        return responseSection;
    }
}
