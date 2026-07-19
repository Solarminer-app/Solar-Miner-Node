package de.verdox.pv_miner.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactFrontendControllerTest {

    private final ReactFrontendController controller = new ReactFrontendController();

    @Test
    void forwardsFrontendRoutesToSpaEntryPoint() {
        assertEquals("forward:/index.html", controller.serveReactApplication());
    }

    @Test
    void forwardsEveryPvConfigRouteToTheReactApplication() throws NoSuchMethodException {
        GetMapping mapping = ReactFrontendController.class
                .getDeclaredMethod("serveReactApplication")
                .getAnnotation(GetMapping.class);
        Set<String> routes = Set.of(mapping.value());

        assertTrue(routes.contains("/config/pv/rest"));
        assertTrue(routes.contains("/config/pv/modbus/tcp"));
        assertTrue(routes.contains("/config/pv/modbus/rtu"));
        assertTrue(routes.contains("/config/pv/mqtt"));
        assertTrue(routes.contains("/config/pv/websocket"));
    }

    @Test
    void packagesReactEntryPointAsSpringStaticResource() {
        assertNotNull(getClass().getClassLoader().getResource("static/index.html"));
    }
}
