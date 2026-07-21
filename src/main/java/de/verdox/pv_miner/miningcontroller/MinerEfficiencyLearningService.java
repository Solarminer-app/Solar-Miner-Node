package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miner.data.MinerStats;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinerEfficiencyLearningService {
    static final int POWER_BUCKET_WATTS = 250;
    static final long MINIMUM_CONFIDENT_SAMPLES = 6;
    private static final Duration MINIMUM_STABLE_DURATION = Duration.ofMinutes(5);
    private static final double MINIMUM_PLAUSIBLE_EFFICIENCY = 5.0;
    private static final double MAXIMUM_PLAUSIBLE_EFFICIENCY = 200.0;
    private static final double MAXIMUM_UPDATE_RATIO = 0.20;
    private static final double LEARNING_RATE = 0.10;

    private final MinerRepository minerRepository;
    private final MinerEfficiencyProfileRepository profileRepository;
    private final EntityQueryService queryService;
    private final Map<UUID, StableTargetObservation> stableTargets = new ConcurrentHashMap<>();
    private final Map<ProfileKey, List<LearningSample>> pendingSamples = new ConcurrentHashMap<>();

    public MinerEfficiencyLearningService(
            MinerRepository minerRepository,
            MinerEfficiencyProfileRepository profileRepository,
            EntityQueryService queryService
    ) {
        this.minerRepository = minerRepository;
        this.profileRepository = profileRepository;
        this.queryService = queryService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void observeStableOperatingPoints() {
        Instant now = Instant.now();
        for (MinerEntity<?> miner : minerRepository.findAll()) {
            MinerStats stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
            if (!isUsable(stats)) {
                stableTargets.remove(miner.getId());
                continue;
            }

            int bucket = bucketFor(stats.powerTargetWatts() > 0
                    ? stats.powerTargetWatts()
                    : stats.approximatedPowerUsageWatts());
            StableTargetObservation observation = stableTargets.compute(miner.getId(), (ignored, current) ->
                    current == null || current.powerTargetBucketWatts() != bucket
                            ? new StableTargetObservation(bucket, now)
                            : current
            );
            if (Duration.between(observation.stableSince(), now).compareTo(MINIMUM_STABLE_DURATION) < 0) {
                continue;
            }

            double efficiency = stats.approximatedPowerUsageWatts() / stats.terahashPerSecond();
            ProfileKey key = new ProfileKey(miner.getId(), bucket);
            pendingSamples.computeIfAbsent(key, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .add(new LearningSample(efficiency, stats.temperatureCelsius(), now));
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    @Transactional
    public void persistLearnedProfiles() {
        Map<ProfileKey, List<LearningSample>> batch = new java.util.HashMap<>();
        pendingSamples.forEach((key, samples) -> {
            synchronized (samples) {
                if (!samples.isEmpty()) {
                    batch.put(key, List.copyOf(samples));
                    samples.clear();
                }
            }
        });

        for (Map.Entry<ProfileKey, List<LearningSample>> entry : batch.entrySet()) {
            ProfileKey key = entry.getKey();
            List<LearningSample> samples = entry.getValue();
            if (!minerRepository.existsById(key.minerId())) {
                stableTargets.remove(key.minerId());
                pendingSamples.remove(key);
                continue;
            }
            double observedMedian = median(samples.stream().map(LearningSample::efficiencyJTh).toList());
            double temperatureAverage = samples.stream()
                    .mapToDouble(LearningSample::temperatureCelsius)
                    .filter(Double::isFinite)
                    .average()
                    .orElse(Double.NaN);

            MinerEfficiencyProfile profile = profileRepository
                    .findByMinerIdAndPowerTargetBucketWatts(key.minerId(), key.powerTargetBucketWatts())
                    .orElseGet(MinerEfficiencyProfile::new);
            if (profile.getId() == null) {
                profile.setMinerId(key.minerId());
                profile.setPowerTargetBucketWatts(key.powerTargetBucketWatts());
                profile.setLearnedEfficiencyJTh(observedMedian);
            } else {
                profile.setLearnedEfficiencyJTh(blend(profile.getLearnedEfficiencyJTh(), observedMedian));
            }
            profile.setSampleCount(profile.getSampleCount() + samples.size());
            if (Double.isFinite(temperatureAverage)) {
                Double previous = profile.getAverageTemperatureCelsius();
                profile.setAverageTemperatureCelsius(previous == null
                        ? temperatureAverage
                        : previous * (1.0 - LEARNING_RATE) + temperatureAverage * LEARNING_RATE);
            }
            profile.setLastObservedAt(samples.stream()
                    .map(LearningSample::observedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.now()));
            profileRepository.save(profile);
        }
    }

    public EfficiencyEstimate estimate(MinerEntity<?> miner, long requestedPowerTargetWatts) {
        List<MinerEfficiencyProfile> profiles = getProfiles(miner.getId());
        Optional<MinerEfficiencyProfile> learned = profiles.stream()
                .filter(profile -> profile.getSampleCount() >= MINIMUM_CONFIDENT_SAMPLES)
                .min(Comparator.comparingInt(profile -> Math.abs(
                        profile.getPowerTargetBucketWatts() - bucketFor(requestedPowerTargetWatts)
                )));
        if (learned.isPresent()) {
            MinerEfficiencyProfile profile = learned.get();
            return new EfficiencyEstimate(
                    profile.getLearnedEfficiencyJTh(), EfficiencySource.LEARNED,
                    profile.getPowerTargetBucketWatts(), profile.getSampleCount()
            );
        }
        if (miner.getNominalEfficiencyJTh() != null && isPlausible(miner.getNominalEfficiencyJTh())) {
            return new EfficiencyEstimate(miner.getNominalEfficiencyJTh(), EfficiencySource.NOMINAL, null, 0);
        }
        return new EfficiencyEstimate(null, EfficiencySource.LIVE, null, 0);
    }

    public List<MinerEfficiencyProfile> getProfiles(UUID minerId) {
        return profileRepository.findByMinerIdOrderByPowerTargetBucketWatts(minerId);
    }

    static int bucketFor(long powerTargetWatts) {
        return (int) Math.max(POWER_BUCKET_WATTS,
                Math.round((double) powerTargetWatts / POWER_BUCKET_WATTS) * POWER_BUCKET_WATTS);
    }

    static double blend(double previous, double observed) {
        double lowerBound = previous * (1.0 - MAXIMUM_UPDATE_RATIO);
        double upperBound = previous * (1.0 + MAXIMUM_UPDATE_RATIO);
        double boundedObservation = Math.max(lowerBound, Math.min(upperBound, observed));
        return previous * (1.0 - LEARNING_RATE) + boundedObservation * LEARNING_RATE;
    }

    private static boolean isUsable(MinerStats stats) {
        if (stats == null || stats.miningStatus() != MinerStats.MinerStatus.MINING
                || stats.terahashPerSecond() <= 0 || stats.approximatedPowerUsageWatts() <= 0) {
            return false;
        }
        return isPlausible(stats.approximatedPowerUsageWatts() / stats.terahashPerSecond());
    }

    private static boolean isPlausible(double efficiency) {
        return Double.isFinite(efficiency)
                && efficiency >= MINIMUM_PLAUSIBLE_EFFICIENCY
                && efficiency <= MAXIMUM_PLAUSIBLE_EFFICIENCY;
    }

    private static double median(List<Double> values) {
        List<Double> ordered = values.stream().sorted().toList();
        int middle = ordered.size() / 2;
        return ordered.size() % 2 == 0
                ? (ordered.get(middle - 1) + ordered.get(middle)) / 2.0
                : ordered.get(middle);
    }

    public enum EfficiencySource { LEARNED, NOMINAL, LIVE }

    public record EfficiencyEstimate(
            Double efficiencyJTh,
            EfficiencySource source,
            Integer powerTargetBucketWatts,
            long sampleCount
    ) {
    }

    private record StableTargetObservation(int powerTargetBucketWatts, Instant stableSince) {
    }

    private record ProfileKey(UUID minerId, int powerTargetBucketWatts) {
    }

    private record LearningSample(double efficiencyJTh, double temperatureCelsius, Instant observedAt) {
    }
}
