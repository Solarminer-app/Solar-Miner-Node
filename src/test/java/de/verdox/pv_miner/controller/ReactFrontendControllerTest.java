package de.verdox.pv_miner.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReactFrontendControllerTest {

    private final ReactFrontendController controller = new ReactFrontendController();

    @Test
    void forwardsFrontendRoutesToSpaEntryPoint() {
        assertEquals("forward:/index.html", controller.serveReactApplication());
    }

    @Test
    void packagesReactEntryPointAsSpringStaticResource() {
        assertNotNull(getClass().getClassLoader().getResource("static/index.html"));
    }
}
