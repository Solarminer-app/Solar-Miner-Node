package de.verdox.pv_miner.controller;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/coin-prices")
public class CoinPriceController {
 private final GlobalConstantsService prices;
 public CoinPriceController(GlobalConstantsService prices) { this.prices=prices; }
 @GetMapping public Map<String,Double> current() { return prices.getCurrentCoinPrices(); }
}
