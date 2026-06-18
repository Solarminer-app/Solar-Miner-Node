package de.verdox.pv_miner.core;

import braiins.bos.v1.*;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RegisterReflectionForBinding({
        ActionsServiceGrpc.class,
        MinerServiceGrpc.class,
        PoolServiceGrpc.class,
        PerformanceServiceGrpc.class,
        CoolingServiceGrpc.class,
        AuthenticationServiceGrpc.class,

        Actions.class,
        Miner.class,
        PoolOuterClass.class,
        Performance.class,
        Work.class,
        Units.class,
        Common.class,
        Cooling.class,
        Authentication.class
})
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
