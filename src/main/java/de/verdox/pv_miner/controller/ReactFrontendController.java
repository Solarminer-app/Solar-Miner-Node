package de.verdox.pv_miner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReactFrontendController {

    @GetMapping({
            "/",
            "/setup",
            "/lightning-wallet",
            "/config/pv/rest",
            "/config/pv/modbus/tcp",
            "/site/{siteId}",
            "/site/{siteId}/dashboard",
            "/site/{siteId}/details",
            "/site/{siteId}/finance",
            "/site/{siteId}/mining",
            "/site/{siteId}/mining/clusters/{clusterName}/config",
            "/site/{siteId}/mining/miners/{minerId}"
    })
    public String serveReactApplication() {
        return "forward:/index.html";
    }
}
