package com.artix.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class ArtixLicensePlugin extends JavaPlugin {
    private static final String CACHE_FILE_NAME = "cache.yml";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private volatile boolean validated;
    private volatile String lastMessage;

    private File cacheFile;
    private YamlConfiguration cache;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.cacheFile = new File(getDataFolder(), CACHE_FILE_NAME);
        this.cache = YamlConfiguration.loadConfiguration(cacheFile);
        this.validated = cache.getBoolean("lastValid", false);
        this.lastMessage = cache.getString("lastMessage", "");

        String licenseKey = getConfig().getString("licenseKey", "").trim();
        if (licenseKey.isBlank()) {
            getLogger().severe("Nenhuma licenseKey configurada em config.yml");
            getLogger().severe("Use /artixlicense set <KEY> ou edite plugins/ArtixLicenseExample/config.yml");
            Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));
            return;
        }

        validateAsync(false);
    }

    @Override
    public void onDisable() {
        saveCache();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("artixlicense")) return false;

        if (args.length == 0) {
            sender.sendMessage("/artixlicense set <KEY>");
            sender.sendMessage("/artixlicense validate");
            sender.sendMessage("/artixlicense status");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage("Uso: /artixlicense set ARTIX-XXXX-XXXX-XXXX-XXXX");
                    return true;
                }
                String key = args[1].trim();
                getConfig().set("licenseKey", key);
                saveConfig();
                sender.sendMessage("License key salva. Rodando validação...");
                validateAsync(true);
                return true;
            }
            case "validate" -> {
                sender.sendMessage("Rodando validação...");
                validateAsync(true);
                return true;
            }
            case "status" -> {
                sender.sendMessage("validated=" + validated);
                sender.sendMessage("lastValid=" + cache.getBoolean("lastValid", false));
                sender.sendMessage("lastMessage=" + cache.getString("lastMessage", ""));
                sender.sendMessage("lastSuccessAt=" + cache.getString("lastSuccessAt", ""));
                return true;
            }
            default -> {
                sender.sendMessage("Subcomando inválido.");
                return true;
            }
        }
    }

    private void validateAsync(boolean force) {
        String apiUrl = getConfig().getString("apiUrl", "").trim();
        String licenseKey = getConfig().getString("licenseKey", "").trim();
        String serverName = getConfig().getString("serverName", "").trim();
        String pluginVersion = getConfig().getString("pluginVersion", "1.0.0").trim();

        long cacheMinutes = Math.max(1, getConfig().getLong("cacheMinutes", 10));
        long offlineGraceMinutes = Math.max(0, getConfig().getLong("offlineGraceMinutes", 60));
        boolean debug = getConfig().getBoolean("debug", false);

        if (apiUrl.isBlank() || licenseKey.isBlank()) {
            getLogger().severe("apiUrl/licenceKey inválidos no config.yml");
            Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));
            return;
        }

        Instant now = Instant.now();
        Instant lastAttemptAt = parseInstant(cache.getString("lastAttemptAt", ""));
        boolean lastValid = cache.getBoolean("lastValid", false);

        if (!force && lastAttemptAt != null) {
            Duration sinceAttempt = Duration.between(lastAttemptAt, now);
            if (sinceAttempt.toMinutes() < cacheMinutes && lastValid) {
                validated = true;
                if (debug) getLogger().info("Usando cache de validação (" + sinceAttempt.toMinutes() + "m)");
                return;
            }
        }

        cache.set("lastAttemptAt", now.toString());
        saveCache();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                ValidationResult result = validateOnce(apiUrl, licenseKey, serverName, pluginVersion);

                cache.set("lastValid", result.valid);
                cache.set("lastMessage", result.message);
                if (result.valid) {
                    cache.set("lastSuccessAt", Instant.now().toString());
                }
                saveCache();

                lastMessage = result.message;
                if (result.valid) {
                    validated = true;
                    getLogger().info("Licença válida: " + result.message);
                    return;
                }

                validated = false;
                getLogger().severe("Licença inválida: " + result.message);
                Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));

            } catch (Exception e) {
                cache.set("lastValid", false);
                cache.set("lastMessage", "Erro de rede: " + e.getClass().getSimpleName());
                saveCache();

                boolean allowOffline = false;
                Instant lastSuccessAt = parseInstant(cache.getString("lastSuccessAt", ""));
                if (lastSuccessAt != null && offlineGraceMinutes > 0) {
                    Duration sinceSuccess = Duration.between(lastSuccessAt, Instant.now());
                    allowOffline = sinceSuccess.toMinutes() <= offlineGraceMinutes;
                }

                if (allowOffline) {
                    validated = true;
                    getLogger().warning("Falha ao validar, mas dentro do offlineGrace. Mantendo plugin ativo.");
                    return;
                }

                validated = false;
                getLogger().severe("Falha ao validar e fora do offlineGrace. Desativando plugin.");
                Bukkit.getScheduler().runTask(this, () -> Bukkit.getPluginManager().disablePlugin(this));
            }
        });
    }

    /**
     * Simple API for other plugins.
     * - If you depend on this plugin, you can call isValidated() to gate features.
     */
    public boolean isValidated() {
        return validated;
    }

    public String getLastMessage() {
        return lastMessage == null ? "" : lastMessage;
    }

    private void saveCache() {
        try {
            if (!getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                getDataFolder().mkdirs();
            }
            cache.save(cacheFile);
        } catch (Exception ignored) {
        }
    }

    private static Instant parseInstant(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return Instant.parse(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private ValidationResult validateOnce(String apiUrl, String licenseKey, String serverName, String pluginVersion) throws Exception {
        // NOTE: API only requires licenseKey.
        String body = toJsonBody(licenseKey, serverName, pluginVersion);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return new ValidationResult(false, "HTTP " + response.statusCode());
        }

        String responseBody = response.body() == null ? "" : response.body();
        String compact = responseBody.replaceAll("\\s+", "");
        boolean valid = compact.contains("\"valid\":true");

        String message = extractJsonString(responseBody, "message");
        if (message == null || message.isBlank()) {
            message = valid ? "Licença válida" : "Licença inválida";
        }

        return new ValidationResult(valid, message);
    }

    private static String toJsonBody(String licenseKey, String serverName, String pluginVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"licenseKey\":\"").append(escapeJson(licenseKey)).append('"');
        if (serverName != null && !serverName.isBlank()) {
            sb.append(",\"serverName\":\"").append(escapeJson(serverName)).append('"');
        }
        if (pluginVersion != null && !pluginVersion.isBlank()) {
            sb.append(",\"pluginVersion\":\"").append(escapeJson(pluginVersion)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Very small JSON helper (no external libs): extracts "key":"value".
     * This is intentionally minimal for integration examples.
     */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;

        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;

        int end = firstQuote + 1;
        boolean escaped = false;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            }
            end++;
        }
        if (end >= json.length()) return null;

        String raw = json.substring(firstQuote + 1, end);
        return raw.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private static final class ValidationResult {
        final boolean valid;
        final String message;

        ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }
}
