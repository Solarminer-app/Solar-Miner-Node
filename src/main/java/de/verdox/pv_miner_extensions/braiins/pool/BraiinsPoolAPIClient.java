package de.verdox.pv_miner_extensions.braiins.pool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.verdox.pv_miner.util.CryptoCurrency;
import de.verdox.vserializer.SerializableField;
import de.verdox.vserializer.generic.Serializer;
import de.verdox.vserializer.generic.SerializerBuilder;
import de.verdox.vserializer.json.JsonSerializerContext;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class BraiinsPoolAPIClient {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static List<DailyReward> getDailyRewards(String authToken, CryptoCurrency cryptoCurrency, LocalDate from, LocalDate to) throws Exception {
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/accounts/rewards/json/" + cryptoCurrency.getId().toLowerCase(Locale.ROOT) + "?from=" + from.toString() + "&to=" + to.toString(), authToken);
        List<DailyReward> list = new LinkedList<>();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        for (JsonElement dailyRewards : jsonElement.getAsJsonObject().get(cryptoCurrency.getId()).getAsJsonObject().get("daily_rewards").getAsJsonArray()) {
            DailyReward dailyReward = DailyReward.SERIALIZER.deserialize(jsonSerializerContext.toElement(dailyRewards));
            list.add(dailyReward);
        }
        return list;
    }

    public static Map<String, WorkerData> getWorkerData(String authToken, CryptoCurrency cryptoCurrency) throws Exception {
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/accounts/workers/json/" + cryptoCurrency.getId().toLowerCase(Locale.ROOT), authToken);
        Map<String, WorkerData> result = new HashMap<>();
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        JsonObject workers = jsonElement.getAsJsonObject().get(cryptoCurrency.getId()).getAsJsonObject().get("workers").getAsJsonObject();
        for (String workerId : workers.keySet()) {
            var element = jsonSerializerContext.toElement(workers.get(workerId).getAsJsonObject());
            result.put(workerId, WorkerData.SERIALIZER.deserialize(element));
        }
        return result;
    }

    public static String getUsername(String authToken, CryptoCurrency cryptoCurrency) throws Exception {
        String coinId = cryptoCurrency.getId().toLowerCase(Locale.ROOT);
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/accounts/profile/json/" + coinId, authToken);
        return jsonElement.getAsJsonObject().get("username").getAsString();
    }

    public static double getCurrentBalance(String authToken, CryptoCurrency cryptoCurrency) throws Exception {
        String coinId = cryptoCurrency.getId().toLowerCase(Locale.ROOT);
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/accounts/profile/json/" + coinId, authToken);
        return jsonElement.getAsJsonObject().getAsJsonObject(coinId).get("current_balance").getAsDouble();
    }

    public static double getTodayReward(String authToken, CryptoCurrency cryptoCurrency) throws Exception {
        String coinId = cryptoCurrency.getId().toLowerCase(Locale.ROOT);
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/accounts/profile/json/" + coinId, authToken);
        return jsonElement.getAsJsonObject().getAsJsonObject(coinId).get("today_reward").getAsDouble();
    }

    public static void ping(String authToken) throws Exception {
        sendGetRequest("https://pool.braiins.com/stats/json/btc", authToken);
    }

    public static PoolStats getPoolStats(String authToken, CryptoCurrency cryptoCurrency) throws Exception {
        JsonElement jsonElement = sendGetRequest("https://pool.braiins.com/stats/json/" + cryptoCurrency.getId().toLowerCase(Locale.ROOT), authToken);
        JsonSerializerContext jsonSerializerContext = new JsonSerializerContext();
        JsonObject object = jsonElement.getAsJsonObject().get(cryptoCurrency.getId()).getAsJsonObject();

        return PoolStats.SERIALIZER.deserialize(jsonSerializerContext.toElement(object));
    }

    private static JsonElement sendGetRequest(String url, String authToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("SlushPool-Auth-Token", authToken)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        try (JsonReader jsonReader = new JsonReader(new StringReader(response.body()))) {
            jsonReader.setLenient(true); // Korrektur für neuere GSON-Versionen
            return JsonParser.parseReader(jsonReader);
        }
    }

    public record DailyReward(
            long date,
            double total_reward,
            double mining_reward,
            double bos_plus_reward,
            double referral_bonus,
            double referral_reward,
            long calculation_date
    ) {
        private static final Serializer<DailyReward> SERIALIZER = SerializerBuilder.create("daily_reward", DailyReward.class)
                .constructor(
                        new SerializableField<>("date", Serializer.Primitive.LONG, DailyReward::date),
                        new SerializableField<>("total_reward", Serializer.Primitive.DOUBLE, DailyReward::total_reward),
                        new SerializableField<>("mining_reward", Serializer.Primitive.DOUBLE, DailyReward::mining_reward),
                        new SerializableField<>("bos_plus_reward", Serializer.Primitive.DOUBLE, DailyReward::bos_plus_reward),
                        new SerializableField<>("referral_bonus", Serializer.Primitive.DOUBLE, DailyReward::referral_bonus),
                        new SerializableField<>("referral_reward", Serializer.Primitive.DOUBLE, DailyReward::referral_reward),
                        new SerializableField<>("calculation_date", Serializer.Primitive.LONG, DailyReward::calculation_date),
                        DailyReward::new
                )
                .build();
    }

    public record PoolStats(double fpps_rate, long updateTimeStamp) {
        public LocalDate getUpdateLocalDate() {
            return Instant.ofEpochSecond(this.updateTimeStamp)
                    .atZone(ZoneId.of("UTC"))
                    .toLocalDate();
        }

        private static final Serializer<PoolStats> SERIALIZER = SerializerBuilder.create("poolstats", PoolStats.class)
                .constructor(
                        new SerializableField<>("fpps_rate", Serializer.Primitive.DOUBLE, PoolStats::fpps_rate),
                        new SerializableField<>("update_ts", Serializer.Primitive.LONG, PoolStats::updateTimeStamp),
                        PoolStats::new
                )
                .build();
    }

    public record WorkerData(
            double lastShare,
            String state,
            String hash_rate_unit,
            double hash_rate_scoring,
            double shares_5m,
            double shares_60m,
            double shares_24h) {

        public Instant getLastShareInstant() {
            return Instant.ofEpochSecond((long) this.lastShare);
        }

        private static final Serializer<WorkerData> SERIALIZER = SerializerBuilder.create("worker_data", WorkerData.class)
                .constructor(
                        new SerializableField<>("lastShare", Serializer.Primitive.DOUBLE, WorkerData::lastShare),
                        new SerializableField<>("state", Serializer.Primitive.STRING, WorkerData::state),
                        new SerializableField<>("hash_rate_unit", Serializer.Primitive.STRING, WorkerData::hash_rate_unit),
                        new SerializableField<>("hash_rate_scoring", Serializer.Primitive.DOUBLE, WorkerData::hash_rate_scoring),
                        new SerializableField<>("shares_5m", Serializer.Primitive.DOUBLE, WorkerData::shares_5m),
                        new SerializableField<>("shares_60m", Serializer.Primitive.DOUBLE, WorkerData::shares_60m),
                        new SerializableField<>("shares_24h", Serializer.Primitive.DOUBLE, WorkerData::shares_24h),
                        WorkerData::new
                )
                .build();
    }

    public record UserProfileResponse(
            String username,
            double all_time_reward,
            String hash_rate_unit,
            double hash_rate_5m,
            double hash_rate_60m,
            double hash_rate_24h,
            double hash_rate_yesterday,
            long low_workers,
            long off_workers,
            long ok_workers,
            long dis_workers,
            double current_balance,
            double today_reward,
            double estimated_reward,
            long shares_5m,
            long shares_60m,
            long shares_24h,
            long shares_yesterday
    ) {
    }
}