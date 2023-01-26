package de.geolykt.playercurrency.interop;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomyException;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;

public class TreasuryCurrencyWrapper implements Currency {

    private final de.geolykt.playercurrency.Currency playerCurrencyCurrency;

    public TreasuryCurrencyWrapper(de.geolykt.playercurrency.Currency pcc) {
        this.playerCurrencyCurrency = pcc;
    }

    @NotNull
    @Override
    public String format(@NotNull BigDecimal value, @Nullable Locale locale, int precision) {
        return playerCurrencyCurrency.format(value.longValue());
    }

    @Override
    @NotNull
    public String format(@NotNull BigDecimal decimal, @Nullable Locale locale) {
        return format(decimal, locale, 2);
    }

    @NotNull
    @Override
    public String getSymbol() {
        return playerCurrencyCurrency.getAbbreviation();
    }

    @Override
    public char getDecimal() {
        return '?';
    }

    @NotNull
    @Override
    public String getDisplayNameSingular() {
        return playerCurrencyCurrency.getName();
    }

    @NotNull
    @Override
    public String getDisplayNamePlural() {
        return playerCurrencyCurrency.getName();
    }

    @Override
    public int getPrecision() {
        return 0;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    @Override
    public void to(@NotNull Currency currency, @NotNull BigDecimal amount, @NotNull EconomySubscriber<BigDecimal> subscription) {
        subscription.fail(new EconomyException(EconomyFailureReason.FEATURE_NOT_SUPPORTED, "Cannot convert between currencies"));
    }

    @Override
    public void parse(@NotNull String formatted, @NotNull EconomySubscriber<BigDecimal> subscription) {
        String[] content = formatted.split(" ");
        if (content.length != 2) {
            subscription.fail(new EconomyException(EconomyFailureReason.NUMBER_PARSING_ERROR));
            return;
        }
        try {
            String parsedCurrency = content[1];
            if (parsedCurrency == null) {
                subscription.fail(new EconomyException(EconomyFailureReason.NUMBER_PARSING_ERROR));
                return;
            }
            if (parsedCurrency.equalsIgnoreCase(playerCurrencyCurrency.abbreviation())
                    || parsedCurrency.equals(playerCurrencyCurrency.name())) {
                subscription.fail(new EconomyException(EconomyFailureReason.NUMBER_PARSING_ERROR));
                return;
            }
            BigDecimal value = BigDecimal.valueOf(Long.valueOf(content[0]));
            if (value == null) {
                throw new InternalError("Someone (or something) messed with the BigDecimal cache.");
            }
            subscription.succeed(value);
        } catch (NumberFormatException ex) {
            subscription.fail(new EconomyException(EconomyFailureReason.NUMBER_PARSING_ERROR, ex));
        }
    }

    @NotNull
    @Override
    public BigDecimal getStartingBalance(@Nullable UUID playerID) {
        BigDecimal ret = BigDecimal.ZERO;
        if (ret == null) {
            throw new InternalError("Someone did some nasty reflection things");
        }
        return ret;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return playerCurrencyCurrency.getAbbreviation();
    }
}
