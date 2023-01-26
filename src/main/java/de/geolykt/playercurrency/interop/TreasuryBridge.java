package de.geolykt.playercurrency.interop;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.geolykt.playercurrency.PlayerCurrency;
import de.geolykt.playercurrency.PlayerProfile;
import de.geolykt.playercurrency.TransferLog;
import de.geolykt.playercurrency.TransferLog.TransferLogEntry;

import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.Account;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.misc.EconomyAPIVersion;
import me.lokka30.treasury.api.economy.misc.OptionalEconomyApiFeature;
import me.lokka30.treasury.api.economy.response.EconomyException;
import me.lokka30.treasury.api.economy.response.EconomyFailureReason;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import me.lokka30.treasury.api.economy.transaction.EconomyTransaction;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionImportance;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionType;

public class TreasuryBridge implements EconomyProvider, BasicInterop {

    public static class TreasuryPlayerAccount implements PlayerAccount {

        @NotNull
        private final UUID uid;

        public TreasuryPlayerAccount(@NotNull UUID id) {
            this.uid = id;
        }


        @NotNull
        @Override
        public UUID getUniqueId() {
            return uid;
        }


        @Override
        public Optional<String> getName() {
            return Optional.ofNullable(Bukkit.getOfflinePlayer(uid).getName());
        }


        @Override
        public void setName(@Nullable String name,
                @NotNull EconomySubscriber<Boolean> subscription) {
            subscription.fail(new EconomyException(EconomyFailureReason.FEATURE_NOT_SUPPORTED));
        }


        @Override
        public void retrieveBalance(@NotNull Currency currency,
                @NotNull EconomySubscriber<BigDecimal> subscription) {
            var pcc = PlayerCurrency.getInstance().getCurrency(currency.getIdentifier());
            if (pcc == null) {
                subscription.fail(new EconomyException(EconomyFailureReason.CURRENCY_NOT_FOUND));
                return;
            }
            BigDecimal ret = BigDecimal.valueOf(PlayerCurrency.getInstance().getBalance(uid, pcc));
            if (ret == null) {
                throw new IllegalStateException("Someone messed with the BigDecimal cache apparently...");
            }
            subscription.succeed(ret);
        }


        @Override
        public void setBalance(@NotNull BigDecimal amount,
                @NotNull EconomyTransactionInitiator<?> initiator, @NotNull Currency currency,
                @NotNull EconomySubscriber<BigDecimal> subscription) {
            var pcc = PlayerCurrency.getInstance().getCurrency(currency.getIdentifier());
            if (pcc == null) {
                subscription.fail(new EconomyException(EconomyFailureReason.CURRENCY_NOT_FOUND));
                return;
            }
            PlayerCurrency.getInstance().setBalance(uid, pcc, amount.longValue(), "Set balance via Treasury API (initiator: " + Objects.toString(initiator.getData()) + ")");
            BigDecimal ret = BigDecimal.valueOf(PlayerCurrency.getInstance().getBalance(uid, pcc));
            if (ret == null) {
                throw new IllegalStateException("Someone messed with the BigDecimal cache apparently...");
            }
            subscription.succeed(ret);
        }

        @Override
        public void doTransaction(@NotNull EconomyTransaction economyTransaction,
                EconomySubscriber<BigDecimal> subscription) {

            var pcc = PlayerCurrency.getInstance().getCurrency(economyTransaction.getCurrencyID());
            if (pcc == null) {
                subscription.fail(new EconomyException(EconomyFailureReason.CURRENCY_NOT_FOUND));
                return;
            }

            String reason = economyTransaction.getReason().orElse("Transaction performed via Treasury.");
            if (reason == null) {
                throw new InternalError();
            }

            ConcurrentSkipListSet<PlayerProfile> baltopProfiles = PlayerCurrency.getInstance().baltops.get(pcc);
            if (baltopProfiles == null) {
                baltopProfiles = PlayerCurrency.getInstance().getBaltopSortedSet(pcc);
                ConcurrentSkipListSet<PlayerProfile> old = PlayerCurrency.getInstance().baltops.putIfAbsent(pcc, baltopProfiles);
                if (old != null) { // race condition :O
                    baltopProfiles = old;
                }
            }

            PlayerProfile profile = PlayerCurrency.getInstance().getProfile(uid);
            TransferLog log = PlayerCurrency.getInstance().getTransferLog();

            long amount = economyTransaction.getTransactionAmount().longValue();
            if (economyTransaction.getTransactionType() == EconomyTransactionType.WITHDRAWAL) {
                amount = -amount;
            }

            AtomicLong old = profile.balances().get(pcc);
            if (old == null) {
                AtomicLong balance = new AtomicLong();
                old = profile.balances().putIfAbsent(pcc, balance);
                if (old == null) {
                    old = balance; // not a race condition
                }
            }

            baltopProfiles.remove(profile);
            BigDecimal ret = BigDecimal.valueOf(old.addAndGet(amount));
            baltopProfiles.add(profile);
            pcc.circulation().addAndGet(amount);
            if (ret == null) {
                throw new InternalError();
            }

            log.pushLog(uid, pcc, amount, reason, economyTransaction.getTimestamp().toEpochMilli());
            subscription.succeed(ret);
        }


        @Override
        public void deleteAccount(@NotNull EconomySubscriber<Boolean> subscription) {
            PlayerProfile profile = PlayerCurrency.getInstance().getProfile(uid);
            profile.balances().entrySet().removeIf((entry) -> {
                Set<PlayerProfile> baltopProfiles = PlayerCurrency.getInstance().baltops.get(entry.getKey());
                if (baltopProfiles != null) {
                    baltopProfiles.remove(profile);
                }
                entry.getKey().circulation().addAndGet(-entry.getValue().getAndSet(0));
                return true;
            });
            subscription.succeed(true);
        }


        @Override
        public void retrieveHeldCurrencies(@NotNull EconomySubscriber<Collection<String>> subscription) {
            List<String> currencies = new ArrayList<>();
            PlayerProfile profile = PlayerCurrency.getInstance().getProfile(uid);
            for (var c : profile.balances().keySet()) {
                currencies.add(c.abbreviation());
            }
            subscription.succeed(currencies);
        }


        @Override
        public void retrieveTransactionHistory(int transactionCount, @NotNull Temporal from,
                @NotNull Temporal to,
                @NotNull EconomySubscriber<Collection<EconomyTransaction>> subscription) {
            PlayerCurrency plugin = PlayerCurrency.getInstance();
            if (plugin == null) {
                throw new IllegalStateException("PlayerCurrency plugin not loaded!");
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Iterator<TransferLogEntry> entries = PlayerCurrency.getInstance().getTransferLog().getLogs();
                Collection<EconomyTransaction> transactions = new ArrayList<>();
                long start = from.getLong(ChronoField.INSTANT_SECONDS) * 1000L;
                long end = to.getLong(ChronoField.INSTANT_SECONDS) * 1000L;
                while (entries.hasNext()) {
                    TransferLogEntry entry = entries.next();
                    if (entry.timestamp() >= start && entry.timestamp() <= end && entry.user().equals(uid)) {
                        String currency = entry.currency();
                        long amount = entry.amount();
                        BigDecimal absAmount = BigDecimal.valueOf(Math.abs(amount));
                        EconomyTransactionInitiator<?> initiator = EconomyTransactionInitiator.SERVER;
                        if (absAmount == null || initiator == null) {
                            throw new InternalError();
                        }
                        transactions.add(new EconomyTransaction(Objects.requireNonNull(currency),
                                initiator,
                                Instant.ofEpochMilli(entry.timestamp()),
                                amount < 0 ? EconomyTransactionType.WITHDRAWAL : EconomyTransactionType.DEPOSIT,
                                entry.reason(),
                                absAmount, EconomyTransactionImportance.NORMAL));
                    }
                }
            });
        }
    }

    @SuppressWarnings("null")
    @Override
    public void createAccount(@Nullable String arg0, @NotNull String arg1,
            @NotNull EconomySubscriber<Account> arg2) {
        arg2.succeed(new TreasuryPlayerAccount(UUID.nameUUIDFromBytes(arg1.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public void createPlayerAccount(@NotNull UUID arg0, @NotNull EconomySubscriber<PlayerAccount> arg1) {
        arg1.succeed(new TreasuryPlayerAccount(arg0));
    }

    @Override
    public Optional<Currency> findCurrency(@NotNull String arg0) {
        var pcc = PlayerCurrency.getInstance().getCurrency(arg0);
        if (pcc == null) {
            return Optional.empty();
        }

        TreasuryCurrencyWrapper wrapper = new TreasuryCurrencyWrapper(pcc);
        return Optional.of(wrapper);
    }

    @Override
    public Set<Currency> getCurrencies() {
        Set<Currency> currencies = new HashSet<>();
        for (var pcc : PlayerCurrency.getInstance().getCurrencies()) {
            currencies.add(new TreasuryCurrencyWrapper(pcc));
        }
        return currencies;
    }

    @Override
    @NotNull
    public Currency getPrimaryCurrency() {
        throw new UnsupportedOperationException("PlayerCurrency is a plugin that does not have a de-facto \"main\" currency,"
                + " as the currencies are defined by the players. As such this operation is not permitted, consider asking the"
                + " plugin for support of such no-primary currency systems.");
    }

    @Override
    @NotNull
    public EconomyAPIVersion getSupportedAPIVersion() {
        return EconomyAPIVersion.v1_0;
    }

    @Override
    @NotNull
    public Set<OptionalEconomyApiFeature> getSupportedOptionalEconomyApiFeatures() {
        return new HashSet<>() {
            private static final long serialVersionUID = -4574761710008939559L;

            {
                // TODO support transaction events
            }
        };
    }

    @Override
    public void hasAccount(@NotNull String arg0, @NotNull EconomySubscriber<Boolean> arg1) {
        arg1.succeed(PlayerCurrency.getInstance().getPlayerUUIDs().contains(UUID.nameUUIDFromBytes(arg0.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public void hasPlayerAccount(@NotNull UUID arg0, @NotNull EconomySubscriber<Boolean> arg1) {
        arg1.succeed(PlayerCurrency.getInstance().getPlayerUUIDs().contains(arg0));
    }

    @Override
    public void registerCurrency(@NotNull Currency arg0, @NotNull EconomySubscriber<Boolean> arg1) {
        arg1.fail(new EconomyException(EconomyFailureReason.FEATURE_NOT_SUPPORTED));
    }

    @SuppressWarnings("null")
    @Override
    public void retrieveAccount(@NotNull String arg0, @NotNull EconomySubscriber<Account> arg1) {
        arg1.succeed(new TreasuryPlayerAccount(UUID.nameUUIDFromBytes(arg0.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public void retrieveAccountIds(@NotNull EconomySubscriber<Collection<String>> arg0) {
        arg0.fail(new EconomyException(EconomyFailureReason.FEATURE_NOT_SUPPORTED));
    }

    @Override
    public void retrieveNonPlayerAccountIds(@NotNull EconomySubscriber<Collection<String>> arg0) {
        arg0.fail(new EconomyException(EconomyFailureReason.FEATURE_NOT_SUPPORTED));
    }

    @Override
    public void retrievePlayerAccount(@NotNull UUID arg0,
            @NotNull EconomySubscriber<PlayerAccount> arg1) {
        arg1.succeed(new TreasuryPlayerAccount(arg0));
    }

    @Override
    public void retrievePlayerAccountIds(@NotNull EconomySubscriber<Collection<UUID>> arg0) {
        arg0.succeed(PlayerCurrency.getInstance().getPlayerUUIDs());
    }

    @Override
    public void onLoad(@NotNull PlayerCurrency instatiator) {
        Bukkit.getServicesManager().register(EconomyProvider.class, this, instatiator, ServicePriority.Normal);
    }
}
