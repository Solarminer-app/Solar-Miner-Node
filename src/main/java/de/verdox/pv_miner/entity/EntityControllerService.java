package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.miner.MinerEntityController;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import de.verdox.pv_miner_extensions.miner.BraiinsOSAsicMinerEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityControllerService {
    private static final Logger LOGGER = Logger.getLogger(EntityControllerService.class.getSimpleName());

    private final Map<Class<? extends ControllableEntity<?>>, Function<? extends ControllableEntity<?>, ? extends EntityController>> controllerFactory = new HashMap<>();
    private final Map<UUID, EntityController> controllers = new WeakHashMap<>();

    public EntityControllerService(MinerApiClient minerApiClient) {
        registerToControllerFactory(AgentMinerEntity.class, agentMinerEntity -> new MinerEntityController(minerApiClient, agentMinerEntity));
        registerToControllerFactory(BraiinsOSAsicMinerEntity.class, braiinsOSAsicMinerEntity -> new MinerEntityController(minerApiClient, braiinsOSAsicMinerEntity));
        registerToControllerFactory(AntminerEntity.class, antminerEntity -> new MinerEntityController(minerApiClient, antminerEntity));
    }

    public <B extends ControllableEntity<C>, C extends EntityController> C getController(B entity) {
        if (!controllerFactory.containsKey(entity.getClass())) {
            throw new IllegalStateException("No controller factory found for type " + entity.getClass().getName());
        }

        if (!controllers.containsKey(entity.getId())) {
            Function<B, C> creator = (Function<B, C>) controllerFactory.get(entity.getClass());
            C controller = creator.apply(entity);
            controllers.put(entity.getId(), controller);
            return controller;
        }

        return (C) controllers.get(entity.getId());
    }

    public <CE extends ControllableEntity<C>, C extends EntityController> void registerToControllerFactory(Class<? extends CE> type, Function<CE, C> creator) {
        controllerFactory.put(type, creator);
    }
}
