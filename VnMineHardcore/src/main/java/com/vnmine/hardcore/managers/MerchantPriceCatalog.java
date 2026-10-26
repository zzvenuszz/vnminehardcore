package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Strict allowlist of vanilla Material sell prices for the system merchant. */
public final class MerchantPriceCatalog {
    private final VnMineHardcore plugin;
    private final File file;
    private Map<Material, SellPrice> prices = Map.of();
    private boolean legacyConfigWarningIssued;

    public MerchantPriceCatalog(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "merchant-prices.yml");
        reload();
    }

    /** Loads the complete allowlist atomically; an invalid file leaves the prior prices active. */
    public boolean reload() {
        if (!file.isFile()) {
            try {
                plugin.saveResource("merchant-prices.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[MerchantPrices] Bundled merchant-prices.yml is missing.",
                    exception);
                return false;
            }
        }
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            Object rawVersion = yaml.get("schema-version");
            if (!(rawVersion instanceof Number version) || version.intValue() != 1
                    || version.doubleValue() != version.intValue()) {
                throw new InvalidConfigurationException("schema-version must be 1");
            }
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items == null || items.getKeys(false).isEmpty()) {
                throw new InvalidConfigurationException("items allowlist must not be empty");
            }
            Map<Material, SellPrice> loaded = new LinkedHashMap<>();
            for (String rawMaterial : items.getKeys(false)) {
                Material material = Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
                ConfigurationSection section = items.getConfigurationSection(rawMaterial);
                if (material == null || !material.isItem() || material.isAir() || section == null) {
                    throw new InvalidConfigurationException("Invalid item material entry: " + rawMaterial);
                }
                Object rawAmount = section.get("amount");
                Object rawVncoin = section.get("vncoin");
                if (!(rawAmount instanceof Number amountNumber)
                        || amountNumber.doubleValue() != amountNumber.intValue()
                        || !(rawVncoin instanceof Number vncoinNumber)
                        || vncoinNumber.doubleValue() != vncoinNumber.longValue()) {
                    throw new InvalidConfigurationException("amount and vncoin must be integers for "
                        + rawMaterial);
                }
                int amount = amountNumber.intValue();
                long vncoin = vncoinNumber.longValue();
                if (amount < 1 || amount > 100_000 || vncoin < 1 || vncoin > 1_000_000L) {
                    throw new InvalidConfigurationException("Invalid amount/vncoin for " + rawMaterial
                        + "; valid amount is 1-100000 and vncoin is 1-1000000");
                }
                if (loaded.putIfAbsent(material, new SellPrice(amount, vncoin)) != null) {
                    throw new InvalidConfigurationException("Duplicate normalized material: " + rawMaterial);
                }
            }
            prices = Map.copyOf(loaded);
            plugin.getLogger().info("[MerchantPrices] Loaded " + prices.size()
                + " vanilla sell prices from " + file.getName() + ".");
            if (!legacyConfigWarningIssued && (plugin.getConfig().contains("economy.merchant.ground-item-default")
                    || plugin.getConfig().contains("economy.merchant.ground-item-price-overrides"))) {
                legacyConfigWarningIssued = true;
                plugin.getLogger().warning("[MerchantPrices] Legacy economy.merchant.ground-item-* keys are ignored;"
                    + " move desired vanilla prices to merchant-prices.yml.");
            }
            if (plugin.getConfig().getBoolean("logging.console-debug", false)) {
                plugin.getLogger().info("[RPG-DEBUG][MerchantPrices] allowlist=" + prices.keySet());
            }
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                "[MerchantPrices] Invalid price catalog; previous valid prices remain active. file="
                    + file.getName(), exception);
            return false;
        }
    }

    public SellPrice get(Material material) {
        return prices.get(material);
    }

    public int size() {
        return prices.size();
    }

    public record SellPrice(int amount, long vncoin) { }
}
