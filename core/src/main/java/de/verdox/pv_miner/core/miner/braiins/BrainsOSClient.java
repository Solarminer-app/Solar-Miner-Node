package de.verdox.pv_miner.core.miner.braiins;

import braiins.bos.v1.*;
import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.MinerStandardCredentials;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.service.DevFeeService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BrainsOSClient {
    public static final String PV_MINER_POOL_GROUP_NAME = "SolarMiner-Pool-Settings";

    private static final Logger LOGGER = Logger.getLogger(BrainsOSClient.class.getSimpleName());

    private final Map<String, TokenDetails> tokenDetailsForEntities = new ConcurrentHashMap<>();
    private final Map<String, Boolean> reachableLastTick = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> activeChannels = new ConcurrentHashMap<>();

    private ManagedChannel getOrCreateChannel(MinerDetails minerDetails) {
        return activeChannels.compute(minerDetails.ipv4(), (id, existingChannel) -> {
            if (existingChannel == null || existingChannel.isShutdown() || existingChannel.isTerminated()) {
                LOGGER.info("Creating new channel to " + minerDetails);
                return ManagedChannelBuilder
                        .forAddress(minerDetails.ipv4(), minerDetails.port())
                        .directExecutor()
                        .usePlaintext()
                        .build();
            }
            return existingChannel;
        });
    }

    private <R, S extends AbstractStub<S>> R createRequest(MinerDetails minerDetails, Function<ManagedChannel, S> stubCreator, Function<S, R> requestLogic, boolean needToken) {
        return createRequest(minerDetails, stubCreator, requestLogic, needToken, true);
    }

    private <R, S extends AbstractStub<S>> R createRequest(MinerDetails minerDetails, Function<ManagedChannel, S> stubCreator, Function<S, R> requestLogic, boolean needToken, boolean needsPing) {
        if (needsPing && !ping(minerDetails)) {
            return null;
        }

        ManagedChannel channel = getOrCreateChannel(minerDetails);

        S stub = stubCreator.apply(channel);
        stub = stub.withDeadlineAfter(5, TimeUnit.SECONDS);

        if (needToken) {
            stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(createAuthorizedHeader(minerDetails)));
        }

        return requestLogic.apply(stub);
    }

    public <R, S extends AbstractStub<S>> R createRequest(MinerDetails minerDetails, Function<ManagedChannel, S> stubCreator, Function<S, R> requestLogic) {
        return createRequest(minerDetails, stubCreator, requestLogic, true);
    }

    public boolean ping(MinerDetails minerDetails) {
        try {
            var response = getCurrentToken(minerDetails, false);
            reachableLastTick.put(minerDetails.ipv4(), true);
            return true;
        } catch (Throwable e) {
            reachableLastTick.put(minerDetails.ipv4(), false);
            return false;
        }
    }

    public boolean startMining(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () ->
                !createRequest(minerDetails, ActionsServiceGrpc::newBlockingStub,
                        stub -> stub.start(Actions.StartRequest.newBuilder().build())
                                .getAlreadyRunning()), false);
    }

    public boolean stopMining(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () ->
                !createRequest(minerDetails, ActionsServiceGrpc::newBlockingStub,
                        stub -> stub.stop(Actions.StopRequest.newBuilder().build())
                                .getAlreadyStopped()), false);
    }

    public boolean pauseMining(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> !createRequest(minerDetails, ActionsServiceGrpc::newBlockingStub,
                stub -> stub.pauseMining(Actions.PauseMiningRequest.newBuilder().build())
                        .getAlreadyPaused()), false);
    }

    public boolean resumeMining(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> !createRequest(minerDetails, ActionsServiceGrpc::newBlockingStub,
                stub -> stub.resumeMining(Actions.ResumeMiningRequest.newBuilder().build())
                        .getAlreadyMining()), false);
    }

    public Miner.MinerStatus getMinerStatus(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, MinerServiceGrpc::newBlockingStub,
                stub -> stub.getMinerDetails(Miner.GetMinerDetailsRequest.newBuilder().build())
                        .getStatus()), Miner.MinerStatus.MINER_STATUS_UNSPECIFIED);
    }

    public Miner.MinerPowerStats getPowerStats(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, MinerServiceGrpc::newBlockingStub,
                stub -> stub.getMinerStats(Miner.GetMinerStatsRequest.newBuilder().build())
                        .getPowerStats()), Miner.MinerPowerStats.getDefaultInstance());
    }

    public List<PoolOuterClass.PoolGroup> getPoolStats(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub,
                stub -> stub.getPoolGroups(PoolOuterClass.GetPoolGroupsRequest.newBuilder().build())
                        .getPoolGroupsList().stream().toList()), List.of());
    }

    public List<PoolOuterClass.Pool> getPoolData(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub,
                stub -> stub.getPoolGroups(PoolOuterClass.GetPoolGroupsRequest.newBuilder().build())
                        .getPoolGroupsList().stream().flatMap(poolGroup -> poolGroup.getPoolsList().stream()).toList()), List.of());
    }

    public Performance.ListTargetProfilesResponse getTargetProfiles(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PerformanceServiceGrpc::newBlockingStub,
                stub -> stub.listTargetProfiles(Performance.ListTargetProfilesRequest.newBuilder().build())), Performance.ListTargetProfilesResponse.getDefaultInstance());
    }

    public Work.WorkSolverStats getMiningStats(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, MinerServiceGrpc::newBlockingStub, stub ->
                stub.getMinerStats(Miner.GetMinerStatsRequest.newBuilder().build())
                        .getMinerStats()), Work.WorkSolverStats.getDefaultInstance());
    }

    public MinerStats.MinerIdentity getInfo(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, MinerServiceGrpc::newBlockingStub, stub -> {
            var details = stub.getMinerDetails(Miner.GetMinerDetailsRequest.newBuilder().build());
            return new MinerStats.MinerIdentity(details.getUid(), details.getMacAddress(), details.getMinerIdentity().getMinerModel());
        }), new MinerStats.MinerIdentity("", "", ""));
    }

    public void enforceAndReplaceDevFee(MinerDetails minerDetails, String poolUrl, String miningAddress, double feePercentage) {
        PoolOuterClass.GetPoolGroupsRequest getRequest = PoolOuterClass.GetPoolGroupsRequest.newBuilder().build();
        PoolOuterClass.GetPoolGroupsResponse response = tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub, stub -> stub.getPoolGroups(getRequest)), null);

        if (response == null) return;

        PoolOuterClass.SetPoolGroupsRequest.Builder setRequestBuilder = PoolOuterClass.SetPoolGroupsRequest.newBuilder().setSaveAction(Common.SaveAction.SAVE_ACTION_SAVE_AND_APPLY);

        for (PoolOuterClass.PoolGroup runtimeGroup : response.getPoolGroupsList()) {
            if (runtimeGroup.getName().equals(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME)) continue;

            PoolOuterClass.PoolGroupConfiguration.Builder groupConfigBuilder = PoolOuterClass.PoolGroupConfiguration.newBuilder()
                    .setName(runtimeGroup.getName());

            if (runtimeGroup.hasQuota()) groupConfigBuilder.setQuota(runtimeGroup.getQuota());
            else if (runtimeGroup.hasFixedShareRatio())
                groupConfigBuilder.setFixedShareRatio(runtimeGroup.getFixedShareRatio());

            boolean isManagedGroup = runtimeGroup.getName().equals(PV_MINER_POOL_GROUP_NAME);

            for (PoolOuterClass.Pool runtimePool : runtimeGroup.getPoolsList()) {
                PoolOuterClass.PoolConfiguration.Builder poolConfigBuilder = PoolOuterClass.PoolConfiguration.newBuilder()
                        .setUrl(runtimePool.getUrl())
                        .setUser(runtimePool.getUser())
                        .setEnabled(isManagedGroup && runtimePool.getEnabled());
                groupConfigBuilder.addPools(poolConfigBuilder.build());
            }
            setRequestBuilder.addPoolGroups(groupConfigBuilder.build());
        }

        double ratio = feePercentage / 100.0;
        PoolOuterClass.PoolConfiguration devPoolConfig = PoolOuterClass.PoolConfiguration.newBuilder()
                .setUrl(poolUrl)
                .setUser(miningAddress)
                .setEnabled(true)
                .build();

        PoolOuterClass.PoolGroupConfiguration devGroupConfig = PoolOuterClass.PoolGroupConfiguration.newBuilder()
                .setName(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME)
                .setFixedShareRatio(PoolOuterClass.FixedShareRatio.newBuilder().setValue(ratio).build())
                .addPools(devPoolConfig)
                .build();

        setRequestBuilder.addPoolGroups(devGroupConfig);
        tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub, stub -> stub.setPoolGroups(setRequestBuilder.build())), null);
    }

    public boolean verifyDevFee(MinerDetails minerDetails, String expectedUrl, String expectedAddress, double expectedPercentage) {
        double expectedRatio = expectedPercentage / 100.0;
        double epsilon = 0.001;

        PoolOuterClass.GetPoolGroupsRequest request = PoolOuterClass.GetPoolGroupsRequest.newBuilder().build();
        PoolOuterClass.GetPoolGroupsResponse response = tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub, stub -> stub.getPoolGroups(request)), null);

        if (response == null) return false;

        String cleanExpectedUrl = expectedUrl.replace("stratum+tcp://", "");
        for (PoolOuterClass.PoolGroup group : response.getPoolGroupsList()) {
            if (group.getName().equals(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME) && group.hasFixedShareRatio()) {
                if (Math.abs(group.getFixedShareRatio().getValue() - expectedRatio) < epsilon) {
                    for (PoolOuterClass.Pool pool : group.getPoolsList()) {
                        if (pool.getUrl().replace("stratum+tcp://", "").contains(cleanExpectedUrl) &&
                                pool.getUser().startsWith(expectedAddress) && pool.getEnabled()) return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean setPowerTarget(MinerDetails minerDetails, long wattTarget) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PerformanceServiceGrpc::newBlockingStub,
                stub -> stub.setPowerTarget(Performance.SetPowerTargetRequest.newBuilder()
                                .setPowerTarget(Units.Power.newBuilder().setWatt(wattTarget))
                                .setSaveAction(Common.SaveAction.SAVE_ACTION_SAVE_AND_APPLY).build())
                        .hasPowerTarget()), false);
    }

    public long getCurrentPowerTarget(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PerformanceServiceGrpc::newBlockingStub, stub ->
                stub.getTunerState(Performance.GetTunerStateRequest.newBuilder().build())
                        .getPowerTargetModeState().getCurrentTarget().getWatt()), 0L);
    }

    public boolean incrementPowerTarget(MinerDetails minerDetails, long increment) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PerformanceServiceGrpc::newBlockingStub,
                stub -> stub.incrementPowerTarget(Performance.IncrementPowerTargetRequest.newBuilder()
                                .setPowerTargetIncrement(Units.Power.newBuilder().setWatt(increment))
                                .setSaveAction(Common.SaveAction.SAVE_ACTION_SAVE_AND_APPLY).build())
                        .hasPowerTarget()), false);
    }

    public boolean decrementPowerTarget(MinerDetails minerDetails, long decrement) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PerformanceServiceGrpc::newBlockingStub, stub ->
                stub.decrementPowerTarget(Performance.DecrementPowerTargetRequest.newBuilder()
                                .setPowerTargetDecrement(Units.Power.newBuilder().setWatt(decrement))
                                .setSaveAction(Common.SaveAction.SAVE_ACTION_SAVE_AND_APPLY).build())
                        .hasPowerTarget()), false);
    }

    public double getTemperatureInDegreeC(MinerDetails minerDetails) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, CoolingServiceGrpc::newBlockingStub,
                stub -> stub.getCoolingState(Cooling.GetCoolingStateRequest.newBuilder().build()).getHighestTemperature().getTemperature().getDegreeC()), 0D);
    }

    private Authentication.LoginResponse getCurrentToken(MinerDetails minerDetails, boolean needsPing) {
        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, AuthenticationServiceGrpc::newBlockingStub,
                stub -> stub.login(Authentication.LoginRequest.newBuilder()
                        .setUsername(minerDetails.username())
                        .setPassword(minerDetails.password())
                        .build()), false, needsPing), Authentication.LoginResponse.getDefaultInstance());
    }

    private Metadata createAuthorizedHeader(MinerDetails minerDetails) {
        Metadata headers = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

        if (tokenDetailsForEntities.containsKey(minerDetails.ipv4())) {
            TokenDetails tokenDetails = tokenDetailsForEntities.get(minerDetails.ipv4());
            long ageInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - tokenDetails.tokenBirthTimeStamp);

            if (tokenDetails.currentToken != null && ageInSeconds < (tokenDetails.timeOutSeconds - 5) && tokenDetails.passwordUsed().equals(minerDetails.password())) {
                headers.put(authKey, tokenDetails.currentToken);
                return headers;
            } else {
                tokenDetailsForEntities.remove(minerDetails.ipv4());
            }
        }

        Authentication.LoginResponse loginResponse = getCurrentToken(minerDetails, true);
        TokenDetails tokenDetails = new TokenDetails(loginResponse.getToken(), loginResponse.getTimeoutS(), System.currentTimeMillis(), minerDetails.password());
        tokenDetailsForEntities.put(minerDetails.ipv4(), tokenDetails);
        headers.put(authKey, tokenDetails.currentToken);
        return headers;
    }

    public boolean setPoolTarget(MinerDetails minerDetails, String stratumUrl, String userName, boolean alsoSetDevFee) {
        PoolOuterClass.GetPoolGroupsRequest getRequest = PoolOuterClass.GetPoolGroupsRequest.newBuilder().build();
        PoolOuterClass.GetPoolGroupsResponse response = tryOrDefault(minerDetails.ipv4(), () ->
                createRequest(minerDetails, PoolServiceGrpc::newBlockingStub, stub -> stub.getPoolGroups(getRequest)), null);
        if (response == null) return false;

        PoolOuterClass.SetPoolGroupsRequest.Builder setRequestBuilder = PoolOuterClass.SetPoolGroupsRequest.newBuilder().setSaveAction(Common.SaveAction.SAVE_ACTION_SAVE_AND_APPLY);
        for (PoolOuterClass.PoolGroup runtimeGroup : response.getPoolGroupsList()) {
            if (runtimeGroup.getName().equals(PV_MINER_POOL_GROUP_NAME) || (alsoSetDevFee && runtimeGroup.getName().equals(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME)))
                continue;

            PoolOuterClass.PoolGroupConfiguration.Builder groupConfigBuilder = PoolOuterClass.PoolGroupConfiguration.newBuilder().setName(runtimeGroup.getName());
            if (runtimeGroup.hasQuota()) groupConfigBuilder.setQuota(runtimeGroup.getQuota());
            else if (runtimeGroup.hasFixedShareRatio())
                groupConfigBuilder.setFixedShareRatio(runtimeGroup.getFixedShareRatio());

            for (PoolOuterClass.Pool runtimePool : runtimeGroup.getPoolsList()) {
                groupConfigBuilder.addPools(PoolOuterClass.PoolConfiguration.newBuilder()
                        .setUrl(runtimePool.getUrl()).setUser(runtimePool.getUser())
                        .setEnabled(runtimeGroup.getName().equals(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME) && runtimePool.getEnabled()));
            }
            setRequestBuilder.addPoolGroups(groupConfigBuilder.build());
        }

        setRequestBuilder.addPoolGroups(PoolOuterClass.PoolGroupConfiguration.newBuilder()
                .setName(PV_MINER_POOL_GROUP_NAME).setQuota(PoolOuterClass.Quota.newBuilder().setValue(1).build())
                .addPools(PoolOuterClass.PoolConfiguration.newBuilder().setUrl(stratumUrl).setUser(userName).setEnabled(true)).build());

        if (alsoSetDevFee) {
            MinerStats.MinerIdentity identity = getInfo(minerDetails);
            String workerName = DevFeeConstants.DEV_FEE_POOL_USER_SHA256 + DevFeeService.sanitizeWorkerName(identity.minerModel() + " " + identity.macAddress());
            setRequestBuilder.addPoolGroups(PoolOuterClass.PoolGroupConfiguration.newBuilder()
                    .setName(DevFeeConstants.DEV_FEE_POOL_GROUP_NAME)
                    .setFixedShareRatio(PoolOuterClass.FixedShareRatio.newBuilder().setValue(DevFeeConstants.DevFeePercentage / 100.0).build())
                    .addPools(PoolOuterClass.PoolConfiguration.newBuilder().setUrl(DevFeeConstants.DEV_FEE_POOL_NAME_SHA256).setUser(workerName).setEnabled(true)).build());
        }

        return tryOrDefault(minerDetails.ipv4(), () -> createRequest(minerDetails, PoolServiceGrpc::newBlockingStub, stub -> {
            stub.setPoolGroups(setRequestBuilder.build());
            return true;
        }), false);
    }

    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        var credentialsToUse = MinerStandardCredentials.byOS(MiningOS.BRAIINS);
        String usernameToUse = credentialsToUse.username() == null ? "" : credentialsToUse.username();
        String passwordToUse = credentialsToUse.password() == null ? "" : credentialsToUse.password();

        Authentication.LoginResponse response = tryOrDefault(details.ipv4(), () ->
                        createRequest(details, AuthenticationServiceGrpc::newBlockingStub,
                                stub -> stub.login(Authentication.LoginRequest.newBuilder()
                                        .setUsername(usernameToUse)
                                        .setPassword(passwordToUse)
                                        .build()), false, false),
                Authentication.LoginResponse.getDefaultInstance());
        return !response.getToken().isEmpty();
    }

    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        Authentication.LoginResponse response = getCurrentToken(details, false);
        return !response.getToken().isEmpty();
    }

    private record TokenDetails(String currentToken, int timeOutSeconds, long tokenBirthTimeStamp,
                                String passwordUsed) {
    }

    private <T> T tryOrDefault(String minerIPV4, Supplier<T> request, T defaultValue) {
        try {
            T t = request.get();
            return t == null ? defaultValue : t;
        } catch (StatusRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNAUTHENTICATED")) {
                tokenDetailsForEntities.remove(minerIPV4);
            } else if (e.getStatus().getCode() == io.grpc.Status.Code.DEADLINE_EXCEEDED ||
                    e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE ||
                    e.getStatus().getCode() == io.grpc.Status.Code.INTERNAL) {
                return defaultValue;
            } else {
                throw new RuntimeException(e);
            }
            return defaultValue;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error: " + minerIPV4, e);
            return defaultValue;
        }
    }
}