package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.dto.PVSiteDetailsDto;
import de.verdox.pv_miner.dto.PVSiteDetailsRequests;
import de.verdox.pv_miner.dto.MoneyDto;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.pvsite.HistoricalPrice;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/pv-site/{siteId}/details")
public class PVSiteDetailsController {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("EUR", "USD", "CHF");
    private static final Set<String> SUPPORTED_LOCALES = Set.of("de", "en");

    private final PVSiteRepository pvSiteRepository;
    private final GlobalConstantsService globalConstantsService;
    private final EntityService entityService;

    public PVSiteDetailsController(
            PVSiteRepository pvSiteRepository,
            GlobalConstantsService globalConstantsService,
            EntityService entityService
    ) {
        this.pvSiteRepository = pvSiteRepository;
        this.globalConstantsService = globalConstantsService;
        this.entityService = entityService;
    }

    @GetMapping
    public PVSiteDetailsDto getDetails(
            @PathVariable UUID siteId,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = pvSiteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found"));

        Locale targetLocale = toLocale(locale);
        CustomCurrency targetCurrency = toCurrency(currency);

        Set<PVPanels> panels = site.getPvPanels() == null ? Set.of() : site.getPvPanels();
        List<PVSiteDetailsDto.PanelGroupDto> panelGroups = panels.stream()
                .sorted(Comparator.comparing(PVPanels::getGroupName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(panel -> new PVSiteDetailsDto.PanelGroupDto(
                        panel.getId(),
                        panel.getGroupName(),
                        panel.getLatitudeDeg(),
                        panel.getLongitudeDeg(),
                        panel.getAmountOfPanels(),
                        panel.getPowerPerPanelInWatts(),
                        panel.getMaxPowerInKw(),
                        panel.getPanelAzimuthDegree(),
                        panel.getPanelSlopeDeg()
                ))
                .toList();

        Set<MinerEntity<?>> siteMiners = site.getMiners() == null ? Set.of() : site.getMiners();
        List<PVSiteDetailsDto.MinerCostDto> miners = siteMiners.stream()
                .sorted(Comparator.comparing(miner -> miner.getName() == null ? "" : miner.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(miner -> new PVSiteDetailsDto.MinerCostDto(
                        miner.getId(),
                        miner.getName(),
                        miner.getIP(),
                        toMoneyDto(miner.getMinerCost(), targetCurrency, targetLocale)
                ))
                .toList();

        int totalPanels = panelGroups.stream()
                .mapToInt(PVSiteDetailsDto.PanelGroupDto::panelCount)
                .sum();
        double totalPeakPowerKw = panelGroups.stream()
                .mapToDouble(PVSiteDetailsDto.PanelGroupDto::peakPowerKw)
                .sum();

        return new PVSiteDetailsDto(
                site.getId(),
                site.getName(),
                site.getTimezoneId() == null ? ZoneId.systemDefault().getId() : site.getTimezoneId(),
                site.getSetupDate(),
                toMoneyDto(site.getPvCost(), targetCurrency, targetLocale),
                totalPeakPowerKw,
                totalPanels,
                panelGroups.size(),
                panelGroups,
                miners,
                toPriceDtos(site.getFeedInTariffHistory(), targetCurrency, targetLocale),
                toPriceDtos(site.getElectricityPriceHistory(), targetCurrency, targetLocale)
        );
    }

    @PutMapping
    public PVSiteDetailsDto updateSiteConfiguration(
            @PathVariable UUID siteId,
            @RequestBody PVSiteDetailsRequests.SiteConfigurationRequest request,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        if (request.setupDate() == null) {
            throw badRequest("Setup date is required");
        }
        if (request.pvCost() < 0) {
            throw badRequest("PV cost must not be negative");
        }

        String timeZone = requireText(request.timeZone(), "Time zone is required");
        try {
            ZoneId.of(timeZone);
        } catch (RuntimeException exception) {
            throw badRequest("Invalid time zone");
        }

        CustomCurrency requestCurrency = toCurrency(request.currency());
        site.setSetupDate(request.setupDate());
        site.setTimezoneId(timeZone);
        site.setPvCost(new Money(request.pvCost(), requestCurrency));
        entityService.save(site);

        return getDetails(siteId, locale, currency);
    }

    @PostMapping("/panel-groups")
    public PVSiteDetailsDto createPanelGroup(
            @PathVariable UUID siteId,
            @RequestBody PVSiteDetailsRequests.PanelGroupRequest request,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        PVPanels panels = new PVPanels();
        panels.setParentSite(site);
        applyPanelGroupRequest(panels, request);
        entityService.save(site, panels);

        return getDetails(siteId, locale, currency);
    }

    @PutMapping("/panel-groups/{panelGroupId}")
    public PVSiteDetailsDto updatePanelGroup(
            @PathVariable UUID siteId,
            @PathVariable UUID panelGroupId,
            @RequestBody PVSiteDetailsRequests.PanelGroupRequest request,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        PVPanels panels = findPanelGroup(site, panelGroupId);
        applyPanelGroupRequest(panels, request);
        entityService.save(site, panels);

        return getDetails(siteId, locale, currency);
    }

    @DeleteMapping("/panel-groups/{panelGroupId}")
    public PVSiteDetailsDto deletePanelGroup(
            @PathVariable UUID siteId,
            @PathVariable UUID panelGroupId,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        PVPanels panels = findPanelGroup(site, panelGroupId);
        entityService.delete(panels);

        return getDetails(siteId, locale, currency);
    }

    @PostMapping("/prices/{priceType}")
    public PVSiteDetailsDto addPrice(
            @PathVariable UUID siteId,
            @PathVariable String priceType,
            @RequestBody PVSiteDetailsRequests.PriceRequest request,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        if (request.validFrom() == null) {
            throw badRequest("Valid-from date is required");
        }
        if (request.amount() < 0) {
            throw badRequest("Price must not be negative");
        }

        List<HistoricalPrice> prices = getPriceHistory(site, priceType);
        prices.removeIf(price -> request.validFrom().equals(price.getValidFrom()));
        prices.add(new HistoricalPrice(
                request.validFrom(),
                new Money(request.amount(), toCurrency(request.currency()))
        ));
        prices.sort(Comparator.naturalOrder());
        entityService.save(site);

        return getDetails(siteId, locale, currency);
    }

    @DeleteMapping("/prices/{priceType}/{validFrom}")
    public PVSiteDetailsDto deletePrice(
            @PathVariable UUID siteId,
            @PathVariable String priceType,
            @PathVariable java.time.LocalDate validFrom,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        List<HistoricalPrice> prices = getPriceHistory(site, priceType);
        boolean removed = prices.removeIf(price -> validFrom.equals(price.getValidFrom()));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Price entry not found");
        }
        entityService.save(site);

        return getDetails(siteId, locale, currency);
    }

    @PutMapping("/miners/{minerId}/cost")
    public PVSiteDetailsDto updateMinerCost(
            @PathVariable UUID siteId,
            @PathVariable UUID minerId,
            @RequestBody PVSiteDetailsRequests.MinerCostRequest request,
            @RequestParam(defaultValue = "de") String locale,
            @RequestParam(defaultValue = "EUR") String currency
    ) {
        PVSiteEntity site = findSite(siteId);
        if (request.amount() < 0) {
            throw badRequest("Miner cost must not be negative");
        }

        MinerEntity<?> miner = (site.getMiners() == null ? Set.<MinerEntity<?>>of() : site.getMiners())
                .stream()
                .filter(candidate -> minerId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Miner not found"));
        miner.setMinerCost(new Money(request.amount(), toCurrency(request.currency())));
        entityService.save(miner, site);

        return getDetails(siteId, locale, currency);
    }

    private List<PVSiteDetailsDto.PriceDto> toPriceDtos(
            List<HistoricalPrice> prices,
            CustomCurrency targetCurrency,
            Locale locale
    ) {
        if (prices == null) {
            return List.of();
        }

        return prices.stream()
                .sorted(Comparator.comparing(HistoricalPrice::getValidFrom).reversed())
                .map(price -> new PVSiteDetailsDto.PriceDto(
                        price.getValidFrom(),
                        toMoneyDto(price.getPrice(), targetCurrency, locale)
                ))
                .toList();
    }

    private PVSiteEntity findSite(UUID siteId) {
        return pvSiteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found"));
    }

    private PVPanels findPanelGroup(PVSiteEntity site, UUID panelGroupId) {
        return (site.getPvPanels() == null ? Set.<PVPanels>of() : site.getPvPanels())
                .stream()
                .filter(panels -> panelGroupId.equals(panels.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Panel group not found"));
    }

    private void applyPanelGroupRequest(
            PVPanels panels,
            PVSiteDetailsRequests.PanelGroupRequest request
    ) {
        String name = requireText(request.name(), "Panel group name is required");
        if (request.panelCount() <= 0) {
            throw badRequest("Panel count must be greater than zero");
        }
        if (request.powerPerPanelWatts() <= 0) {
            throw badRequest("Power per panel must be greater than zero");
        }
        if (request.latitude() < -90 || request.latitude() > 90) {
            throw badRequest("Latitude must be between -90 and 90");
        }
        if (request.longitude() < -180 || request.longitude() > 180) {
            throw badRequest("Longitude must be between -180 and 180");
        }
        if (request.azimuthDegrees() < 0 || request.azimuthDegrees() > 360) {
            throw badRequest("Azimuth must be between 0 and 360");
        }
        if (request.slopeDegrees() < 0 || request.slopeDegrees() > 90) {
            throw badRequest("Slope must be between 0 and 90");
        }
        panels.setGroupName(name);
        panels.setLatitudeDeg(request.latitude());
        panels.setLongitudeDeg(request.longitude());
        panels.setAmountOfPanels(request.panelCount());
        panels.setPowerPerPanelInWatts(request.powerPerPanelWatts());
        panels.setPanelAzimuthDegree(request.azimuthDegrees());
        panels.setPanelSlopeDeg(request.slopeDegrees());
    }

    private List<HistoricalPrice> getPriceHistory(PVSiteEntity site, String priceType) {
        if ("feed-in".equals(priceType)) {
            if (site.getFeedInTariffHistory() == null) {
                site.setFeedInTariffHistory(new ArrayList<>());
            }
            return site.getFeedInTariffHistory();
        }
        if ("electricity".equals(priceType)) {
            if (site.getElectricityPriceHistory() == null) {
                site.setElectricityPriceHistory(new ArrayList<>());
            }
            return site.getElectricityPriceHistory();
        }
        throw badRequest("Unsupported price type");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private MoneyDto toMoneyDto(
            Money money,
            CustomCurrency targetCurrency,
            Locale locale
    ) {
        Money source = money == null ? new Money(0, targetCurrency) : money;
        Money converted = source.getCurrency().getCurrencyCode().equals(targetCurrency.getCurrencyCode())
                ? source
                : globalConstantsService.convert(source, targetCurrency);

        return new MoneyDto(
                converted.getRawMoneyAmount(),
                targetCurrency.getCurrencyCode(),
                targetCurrency.format(converted.getRawMoneyAmount(), locale)
        );
    }

    private Locale toLocale(String locale) {
        String normalized = locale == null ? "de" : locale.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LOCALES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported locale");
        }
        return Locale.forLanguageTag(normalized);
    }

    private CustomCurrency toCurrency(String currency) {
        String normalized = currency == null ? "EUR" : currency.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency");
        }
        return CustomCurrency.getInstance(normalized);
    }
}
