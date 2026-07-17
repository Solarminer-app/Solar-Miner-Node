package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.configuration.AbstractConfigStorage;
import de.verdox.pv_miner.miningcontroller.dsl.DefaultCluster;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Stream;

@Service
public class MinerControllerConfigStorage extends AbstractConfigStorage<MinerControllerConfig> {
    public static final String STANDARD_CLUSTER_NAME = "Standard";
    private final Map<String, MinerControllerConfig> configs = new HashMap<>();

    public MinerControllerConfigStorage() {
        super(new File("./storage/miner/"), MinerControllerConfig.SERIALIZER);

        if(configs.isEmpty()) {
            try {
                save(STANDARD_CLUSTER_NAME, DefaultCluster.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save(String nameOfConfig, MinerControllerConfig minerControllerConfig) throws IOException {
        save(getFile(nameOfConfig), minerControllerConfig);
        configs.put(nameOfConfig, minerControllerConfig);
    }

    public boolean delete(String nameOfConfig) {
        var result = delete(getFile(nameOfConfig));
        if (result) {
            configs.remove(nameOfConfig);
        }
        return result;
    }

    public MinerControllerConfig get(String nameOfConfig) throws IOException {
        if (configs.containsKey(nameOfConfig)) {
            return configs.get(nameOfConfig);
        }
        File file = getFile(nameOfConfig);
        var loaded = load(file);
        configs.put(nameOfConfig, loaded);
        return loaded;
    }

    public List<String> getNameOfSavedConfigs() throws IOException {
        return getNameOfSavedFiles(getStorageFolder());
    }

    @Override
    public MinerControllerConfig createDefaultValue() {
        return new MinerControllerConfig(new HashMap<>());
    }

    private File getFile(String name) {
        return new File(getStorageFolder() + "/" + name + ".json");
    }
}
