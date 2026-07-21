export interface PVSite {
    id: string;
    name: string;
}

export interface StartViewData {
    sites: PVSite[];
    currentCount: number;
    limit: number;
    limitExceeded: boolean;
}

export interface MoneyDto {
    amount: number;
    currency: string;
    formatted?: string;
}

export interface LiveEnergyDto {
    pvPowerKw: number;
    householdPowerKw: number;
    minerPowerKw: number;
    totalLoadKw: number;
    gridImportKw: number;
    gridExportKw: number;
    batteryPowerKw: number;
    batterySocPercent: number;
    batteryCapacityKwh: number;
    estimatedBatteryRuntimeHours: number | null;
    batteryState: 'CHARGING' | 'DISCHARGING' | 'IDLE';
}

export interface DailyEnergySummaryDto {
    productionKwh: number;
    consumptionKwh: number;
    householdConsumptionKwh: number;
    miningConsumptionKwh: number;
    gridImportKwh: number;
    gridExportKwh: number;
    selfConsumedKwh: number;
    selfConsumptionPercent: number;
    autarkyPercent: number;
    miningLocalEnergyKwh: number;
    miningGridEnergyKwh: number;
    exportRevenue: number;
    importCost: number;
    householdSavings: number;
    miningOpportunityCost: number;
    minedSats: number;
    miningRevenue: number;
    miningNetResult: number;
    currencySymbol: string;
}

export interface MiningOverviewDto {
    totalHashrateThs: number;
    actualPowerWatts: number;
    targetPowerWatts: number;
    efficiencyJPerTh: number;
    activeMiners: number;
    totalMiners: number;
    estimatedPvPowerWatts: number;
    estimatedBatteryPowerWatts: number;
    estimatedGridPowerWatts: number;
    clusterName: string;
    controllerRunning: boolean;
    controllerMode: string;
    lastControllerAction: string;
}

export interface DataQualityDto {
    sourceStatus: 'ONLINE' | 'STALE' | 'OFFLINE' | 'WAITING';
    sourceType: string;
    measuredAt: string | null;
    ageSeconds: number;
}

export interface MinerLockStatusDto {
    minerName: string;
    ipAddress: string;
    stateLockRemainingSeconds: number;
    powerLockRemainingSeconds: number;
    expectedPowerWatts: number;
}

export interface LiveKpiDto {
    pvPower: string;
    minerPower: string;
    powerTotal: string;
    liveImport: string;
    liveExport: string;
    batterySoc: string;
    batteryPower: string;
    totalHashrate: string;
    activeMiners: string;
}

export interface DailyFinancialStatsDto {
    exportedToday: string;
    revenueExportToday: string;
    importToday: string;
    costImportToday: string;
    loadHomeTotalToday: string;
    avoidedEnergyCost: string;
    loadMinerTotalToday: string;
    minerNotExported: string;
}

export interface LiveDashboardUpdateDto {
    kpi: LiveKpiDto;
    lockStatusDtos: MinerLockStatusDto[];
    financials: DailyFinancialStatsDto;
    walletBalanceFormatted: string;
    energy: LiveEnergyDto;
    day: DailyEnergySummaryDto;
    mining: MiningOverviewDto;
    dataQuality: DataQualityDto;
}

export interface MinerDashboardItemDTO {
    name: string;
    ip: string;
    status: string;
    hashrate: string;
    power: string;
    temp: string;
    pool: string;
    stateLockRemainingSeconds: number;
    powerLockRemainingSeconds: number;
    controllerPowerTarget: number;
}

export interface DashboardPoolDto {
    id: string;
    url: string;
    worker: string;
    status: string;
}

export interface DashboardInitDto {
    siteName: string;
    miners: MinerDashboardItemDTO[];
    pools: DashboardPoolDto[];
    chartData: DashboardChartDatapointDto[];
}

export interface DashboardChartDatapointDto {
    timestamp: number;
    powerKw: number;
    allocatedKw: number;
}

export interface SeriesPointDto {
    timestamp: number;
    value: number;
}

export interface ControllerChartPointDto {
    timestamp: number;
    targetPowerWatts: number;
    allocatedPowerWatts: number;
    activeMode: string;
    event: string | null;
}

export interface DashboardChartsDto {
    live: {
        pvPower: SeriesPointDto[];
        gridImport: SeriesPointDto[];
        gridExport: SeriesPointDto[];
        consumption: SeriesPointDto[];
        minerConsumption: SeriesPointDto[];
    };
    pvHistory: SeriesPointDto[];
    controller: {
        clusterName: string;
        running: boolean;
        points: ControllerChartPointDto[];
    };
    clusterNames: string[];
}

export interface PanelGroupDto {
    id: string;
    name: string | null;
    latitude: number;
    longitude: number;
    panelCount: number;
    powerPerPanelWatts: number;
    peakPowerKw: number;
    azimuthDegrees: number;
    slopeDegrees: number;
}

export interface MinerCostDto {
    id: string;
    name: string;
    ipAddress: string;
    cost: MoneyDto;
}

export interface PriceDto {
    validFrom: string;
    price: MoneyDto;
}

export interface PVSiteDetailsDto {
    siteId: string;
    name: string;
    timeZone: string;
    setupDate: string;
    pvCost: MoneyDto;
    totalPeakPowerKw: number;
    totalPanels: number;
    totalGroups: number;
    panelGroups: PanelGroupDto[];
    pvDevices: PvDeviceDto[];
    miners: MinerCostDto[];
    feedInTariffs: PriceDto[];
    electricityPrices: PriceDto[];
}

export interface PvDeviceDto {
    id: string;
    deviceType: 'INVERTER' | 'BATTERY' | 'SMART_METER';
    name: string;
    providerId: string;
    host: string;
    port: number;
    slaveId: number;
    profileName: string;
    sectionKey: string;
}

export interface FinanceKpiDto {
    totalInvestment: MoneyDto;
    realizedProfit: MoneyDto;
    unrealizedValue: MoneyDto;
    allTimeMinedBtc: number;
    allTimeSoldBtc: number;
    unsoldBtc: number;
    roiProgressPercent: number;
    totalOpex: MoneyDto;
    totalHouseholdSavings: MoneyDto;
    totalFeedInRevenue: MoneyDto;
    estimatedBreakEvenDate: string | null;
}

export interface FinanceInsightsDto {
    miningRevenueHistoric: MoneyDto;
    miningRevenueLive: MoneyDto;
    miningEnergyCost: MoneyDto;
    miningGridCost: MoneyDto;
    miningOpportunityCost: MoneyDto;
    miningNetHistoric: MoneyDto;
    miningNetLive: MoneyDto;
    householdSavings: MoneyDto;
    feedInRevenue: MoneyDto;
    totalValueCreated: MoneyDto;
    operatingResult: MoneyDto;
    averageDailyOperatingResult: MoneyDto;
    costPerMinedBtc: MoneyDto;
    breakEvenBtcPrice: MoneyDto;
    totalCapitalValue: MoneyDto;
    totalCapitalCost: MoneyDto;
    netPosition: MoneyDto;
    remainingToBreakEven: MoneyDto;
    averageDailyCapitalValue: MoneyDto;
    minedBtc: number;
    miningEnergyKwh: number;
    gridMiningSharePercent: number;
    profitableMiningDays: number;
    daysWithData: number;
    bestDay: string | null;
    bestDayResult: MoneyDto;
    worstDay: string | null;
    worstDayResult: MoneyDto;
}

export interface PVStatisticDto {
    date: string;
    totalPvProduction: number;
    minerConsumption: number;
    miningPvUsage: number;
    miningGridUsage: number;
    householdPvUsage: number;
    exportedKwh: number;
    minedBtc: number;
    miningCost: MoneyDto;
    miningGridCost: MoneyDto;
    miningOpportunityCost: MoneyDto;
    effectiveYieldPerKwh: MoneyDto;
    btcLiveValue: MoneyDto;
    btcHistoricValue: MoneyDto;
    householdSavings: MoneyDto;
    feedInRevenue: MoneyDto;
    feedInPricePerKwh: MoneyDto;
}

export interface BitcoinSaleDto {
    saleDate: string;
    amountBtc: number;
    fiatValue: MoneyDto;
}

export interface FinancePageDto {
    setupDate: string;
    from: string;
    to: string;
    filteredKpis: FinanceKpiDto;
    allTimeKpis: FinanceKpiDto;
    periodInsights: FinanceInsightsDto;
    allTimeInsights: FinanceInsightsDto;
    days: PVStatisticDto[];
    sales: BitcoinSaleDto[];
}

export interface MinerDto {
    id: string;
    name: string;
    ipAddress: string;
    model: string;
    status: string;
    hashrateThs: number;
    powerWatts: number;
    temperatureCelsius: number;
    pool: string | null;
    hardwareMinPowerWatts: number;
    hardwareDefaultPowerWatts: number;
    hardwareMaxPowerWatts: number;
    configuredMinPowerWatts: number;
    configuredMaxPowerWatts: number;
    supportsDynamicPowerScaling: boolean;
    powerStepWatts: number | null;
    minimumRunMinutes: number | null;
    minimumIdleMinutes: number | null;
    powerChangeLockMinutes: number | null;
}

export interface ClusterDto {
    name: string;
    running: boolean;
    miners: MinerDto[];
}

export interface MiningPoolDto {
    id: string;
    type: string;
    name: string;
    stratumUrl: string;
}

export interface DiscoveredMinerDto {
    model: string;
    ipAddress: string;
    operatingSystem: string;
}

export interface MiningPageDto {
    siteName: string;
    totalClusters: number;
    activeClusters: number;
    totalMiners: number;
    totalHashrateThs: number;
    clusters: ClusterDto[];
    connectedMiners: MinerDto[];
    unassignedMiners: MinerDto[];
    connectedPools: MiningPoolDto[];
    devFee: DevFeeOverviewDto;
}

export interface MiningLiveSnapshotDto {
    updatedAt: string;
    totalHashrateThs: number;
    miners: Array<{
        id: string;
        status: string;
        hashrateThs: number;
        powerWatts: number;
        temperatureCelsius: number;
    }>;
}

export interface DevFeeOverviewDto {
    backendAvailable: boolean;
    userPercentage: number;
    totalFeePercentage: number;
    referralCode: string | null;
    referralValid: boolean;
    allocations: Array<{
        beneficiaryType: 'SOLARMINER' | 'REFERRAL' | string;
        beneficiaryName: string;
        percentage: number;
    }>;
}

export interface ClusterConditionDto {
    type: 'LOGICAL' | 'PREDICATE';
    operator: 'AND' | 'OR' | 'NOT' | null;
    subConditions: ClusterConditionDto[];
    variable: string | null;
    aggregation: string | null;
    windowValue: number | null;
    windowUnit: string | null;
    comparator: string | null;
    threshold: number | null;
}

export interface ClusterValueExpressionDto {
    type: 'CONSTANT' | 'DYNAMIC_VARIABLE' | 'CAPACITY_PERCENTAGE';
    constantWatts: number | null;
    variable: string | null;
    aggregation: string | null;
    windowValue: number | null;
    windowUnit: string | null;
    multiplier: number | null;
    offset: number | null;
    percentage: number | null;
}

export interface ClusterControllerActionDto {
    actionType: string;
    targetType: string;
    strategy: string;
    valueExpression: ClusterValueExpressionDto;
    stepSizeWatts: number;
}

export interface ClusterOperatingModeDto {
    name: string;
    startCondition: ClusterConditionDto;
    stopCondition: ClusterConditionDto;
    actions: ClusterControllerActionDto[];
    minRunTimeMinutes: number;
    minIdleTimeMinutes: number;
    powerChangeLockTimeMinutes: number;
}

export interface ClusterSimulationPresetDto {
    id: string;
    labelKey: string;
    descriptionKey: string;
}

export interface ClusterDslOptionsDto {
    variables: string[];
    comparators: string[];
    logicalOperators: string[];
    actionTypes: string[];
    targetTypes: string[];
    distributionStrategies: string[];
    aggregations: string[];
    timeUnits: string[];
    expressionTypes: string[];
    simulationPresets: ClusterSimulationPresetDto[];
}

export interface ClusterConfigDto {
    name: string;
    existing: boolean;
    modes: ClusterOperatingModeDto[];
    options: ClusterDslOptionsDto;
}

export interface ClusterSimulationPointDto {
    timestamp: string;
    pvPowerKw: number;
    loadPowerKw: number;
    batterySocPercent: number;
    minerPowerKw: number;
    potentialSurplusKw: number;
    targetPowerWatts: number;
    allocatedPowerWatts: number;
    activeMiners: number;
    activeMode: string;
}

export interface ClusterSimulationDto {
    sourceType: string;
    sourceLabel: string;
    simulatedDate: string;
    clusterCapacityWatts: number;
    points: ClusterSimulationPointDto[];
    summary: {
        simulatedEnergyKwh: number;
        pvPoweredEnergyKwh: number;
        estimatedGridEnergyKwh: number;
        peakTargetWatts: number;
        modeChanges: number;
        activeMinutes: number;
        mostActiveMode: string;
    };
}

export interface MinerDetailsPageDto {
    id: string;
    name: string | null;
    ipAddress: string;
    operatingSystem: string;
    model: string;
    uid: string;
    macAddress: string;
    status: string;
    clusterName: string | null;
    configuredPool: string | null;
    live: {
        hashrateThs: number;
        powerUsageWatts: number;
        powerTargetWatts: number;
        temperatureCelsius: number;
        efficiencyJTh: number;
    };
    hardware: {
        hardwareMinPowerWatts: number;
        hardwareDefaultPowerWatts: number;
        hardwareMaxPowerWatts: number;
        configuredMinPowerWatts: number;
        configuredMaxPowerWatts: number;
        powerStepWatts: number | null;
        minimumRunMinutes: number | null;
        minimumIdleMinutes: number | null;
        powerChangeLockMinutes: number | null;
    };
    efficiencyStrategy: {
        dispatchPriority: number | null;
        nominalEfficiencyJTh: number | null;
        effectiveEfficiencyJTh: number | null;
        effectiveSource: 'LEARNED' | 'NOMINAL' | 'LIVE';
        effectivePowerTargetBucketWatts: number | null;
        effectiveSampleCount: number;
        learnedProfiles: Array<{
            powerTargetBucketWatts: number;
            learnedEfficiencyJTh: number;
            sampleCount: number;
            averageTemperatureCelsius: number | null;
            lastObservedAt: string;
            controllerReady: boolean;
        }>;
    };
    pools: Array<{url: string; username: string}>;
    workers: Array<{
        name: string;
        algorithm: string;
        status: string;
        hashrateThs: number;
        temperatureCelsius: number;
        powerUsageWatts: number;
    }>;
    historySummary: {
        from: string;
        to: string;
        dataPoints: number;
        averageHashrateThs: number;
        averagePowerWatts: number;
        averageEfficiencyJTh: number;
        maximumTemperatureCelsius: number;
        estimatedEnergyKwh: number;
    };
    history: Array<{
        timestamp: string;
        hashrateThs: number | null;
        powerUsageWatts: number | null;
        powerTargetWatts: number | null;
        temperatureCelsius: number | null;
        efficiencyJTh: number | null;
    }>;
}
