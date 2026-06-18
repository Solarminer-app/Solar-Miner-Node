package de.verdox.solarminer.pcagent.xmr;

import de.verdox.solarminer.pcagent.dto.Pools;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
@EnableScheduling
public class XmrDevFeeManager {

    private static final Logger LOGGER = Logger.getLogger(XmrDevFeeManager.class.getName());
    private static final Path CONFIG_PATH = Paths.get("./solarminer-agent/xmrig/config.json").toAbsolutePath().normalize();

    private final XmrMinerService minerService;
    private final XmrConfigService configService;

    private String devPoolUrl = "stratum+tcp://randomxmonero.auto.nicehash.com:9200";
    private String devWallet = "NHbTNKmjJ5DSrTuLhHWs3AYKzfruK4Pn9RUD";
    private boolean devUseTls = false;
    private double devFeePercentage = 2.5;

    private boolean isCurrentlyDevMining = false;

    private String userPoolUrl;
    private String userWallet;
    private boolean userUseTls;
    private long devFeeMaxMinutes;
    private int elapsedDevMinutes;

    public XmrDevFeeManager(XmrMinerService minerService, XmrConfigService configService) {
        this.minerService = minerService;
        this.configService = configService;
        setDevFee(2.5);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void readConfigAndSetup() {
        Pools pools = configService.readUserPoolFromConfig();
        registerUserConfig(pools.poolUrl(), pools.poolUsername(), false);
    }

    public void setDevFee(double fee) {
        this.devFeePercentage = fee;
        this.devFeeMaxMinutes = (long) (TimeUnit.DAYS.toMinutes(1) * devFeePercentage);
        LOGGER.info("Dev Fee is at " + String.format("%.2f", (fee)) + " %. We will make sure you won't mine longer than " + devFeeMaxMinutes + " minutes");
    }

    public void registerUserConfig(String poolUrl, String wallet, boolean useTls) {
        this.userPoolUrl = poolUrl;
        this.userWallet = wallet;
        this.userUseTls = useTls;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void trackAndSwitchDevFee() {
        if (!minerService.isMiningProcessAlive()) return;

        if (isCurrentlyDevMining) {
            elapsedDevMinutes += 5;
            if (elapsedDevMinutes >= TimeUnit.MINUTES.toDays(1) * devFeePercentage) {
                switchToUser();
            }
            return;
        }

        if (Math.random() < (devFeePercentage / 100.0)) {
            LOGGER.info("Switching to Dev Fee mining...");
            switchToDevFee();
        }
    }

    private void switchToDevFee() {
        isCurrentlyDevMining = true;
        elapsedDevMinutes = 0;

        minerService.hardStopMining();

        configService.configureXmrig(CONFIG_PATH, devPoolUrl, devWallet, devUseTls);

        minerService.startMining();
        LOGGER.info("Successfully switched XMRig to Developer Pool.");
    }

    private void switchToUser() {
        if (userWallet == null || userPoolUrl == null) {
            LOGGER.severe("Cannot switch to user pool: User config missing!");
            return;
        }

        isCurrentlyDevMining = false;
        elapsedDevMinutes = 0;

        minerService.hardStopMining();

        configService.configureXmrig(CONFIG_PATH, userPoolUrl, userWallet, userUseTls);

        minerService.startMining();
        LOGGER.info("Successfully switched XMRig back to User Pool.");
    }
}
