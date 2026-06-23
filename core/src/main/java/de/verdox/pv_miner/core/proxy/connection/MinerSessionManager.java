package de.verdox.pv_miner.core.proxy.connection;

import de.verdox.pv_miner.core.proxy.MinerSession;
import io.netty.channel.Channel;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class MinerSessionManager {

    private final ApplicationContext context;

    public MinerSessionManager(ApplicationContext context) {
        this.context = context;
    }

    public MinerSession createSession(Channel minerChannel, String coinName) {
        MinerSession session = context.getBean(MinerSession.class);
        session.initialize(minerChannel, coinName);
        return session;
    }
}