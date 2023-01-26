package de.geolykt.playercurrency;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import de.geolykt.playercurrency.interop.BasicInterop;

public class PlayerCurrency extends JavaPlugin {

    private static PlayerCurrency instance;
    // Numbers chosen by "fair" dice-roll
    @NotNull
    public static final UUID CENTRAL_COPPER_INDEX = new UUID(0x3E1B_73EC_7849_3B3EL, 0x7C2F_086A_44CF_5A70L);
    @NotNull
    public static final UUID CENTRAL_IRON_INDEX = new UUID(0x58AC_9BBF_D25F_0A56L, 0x90FA_8C71_9C48_D7F2L);
    @NotNull
    public static final UUID CENTRAL_DIAMOND_INDEX = new UUID(0x25FD_25DD_DEC5_C425L, 0x2BE8_BFE4_1CE7_59C1L);

    private static final Long DEFAULT_BALANCE = 0L;

    @NotNull
    private static final Locale DEFAULT_LOCALE;

    @SuppressWarnings("null")
    @NotNull
    public static final TextColor HELP_ARGUMENT_COLOR = NamedTextColor.AQUA;
    @SuppressWarnings("null")
    @NotNull
    public static final TextColor HELP_COMMAND_COLOR = NamedTextColor.GOLD;
    @NotNull
    private static final Component PLUGIN_LOGO = Component.text('[', NamedTextColor.GOLD)
            .append(Component.text()
                    .color(NamedTextColor.GREEN)
                    .content("Player")
                    .decorate(TextDecoration.BOLD))
            .append(Component.text()
                    .color(NamedTextColor.DARK_AQUA)
                    .content("Currencies")
                    .decorate(TextDecoration.BOLD))
            .append(Component.text(']', NamedTextColor.GOLD))
            .append(Component.space());

    private static CentralOreIndices centralOreIndicesListener;
    static {
        Locale a = Locale.getDefault(Category.DISPLAY);
        if (a == null) {
            throw new IllegalStateException("Unable to find default locale.");
        }
        DEFAULT_LOCALE = a;
    }

    public static PlayerCurrency getInstance() {
        return PlayerCurrency.instance;
    }

    @NotNull
    private final Map<UUID, Currency> currencies = new ConcurrentHashMap<>();

    @NotNull
    private final Map<String, Currency> abbrevToCurrency = new ConcurrentHashMap<>();

    @NotNull
    private final Map<String, Currency> nameToCurrency = new ConcurrentHashMap<>();

    @NotNull
    private final Map<UUID, PlayerProfile> balances = new ConcurrentHashMap<>();

    @NotNull
    public final Map<Currency, ConcurrentSkipListSet<PlayerProfile>> baltops = new ConcurrentHashMap<>();

    @SuppressWarnings("null")
    @NotNull
    public final Set<UUID> currencyCreators = ConcurrentHashMap.newKeySet();

    @NotNull
    private final TransferLog log = new TransferLog();

    private boolean loadSuccess = false;

    public void addBalance(@NotNull UUID player, @NotNull Currency currency, long amount, @NotNull String reasoning) {
        Objects.requireNonNull(reasoning, "Null reasoning");
        PlayerProfile profile = getProfile(player);

        ConcurrentSkipListSet<PlayerProfile> baltopProfiles = baltops.get(currency);
        if (baltopProfiles == null) {
            baltopProfiles = getBaltopSortedSet(currency);
            ConcurrentSkipListSet<PlayerProfile> old = baltops.putIfAbsent(currency, baltopProfiles);
            if (old != null) { // race condition :O
                baltopProfiles = old;
            }
        }

        AtomicLong old = profile.balances().get(currency);
        if (old == null) {
            AtomicLong bal = new AtomicLong(DEFAULT_BALANCE);
            old = profile.balances().putIfAbsent(currency, bal);
            if (old == null) {
                old = bal; // No race condition
            }
        }

        baltopProfiles.remove(profile);
        old.getAndAdd(amount);
        baltopProfiles.add(profile);
        currency.circulation().addAndGet(amount);
        log.pushLog(player, currency, amount, reasoning);
    }

    public void addBalanceNoLog(@NotNull UUID player, @NotNull Currency currency, long amount) {
        PlayerProfile profile = getProfile(player);

        ConcurrentSkipListSet<PlayerProfile> baltopProfiles = baltops.get(currency);
        if (baltopProfiles == null) {
            baltopProfiles = getBaltopSortedSet(currency);
            ConcurrentSkipListSet<PlayerProfile> old = baltops.putIfAbsent(currency, baltopProfiles);
            if (old != null) { // race condition :O
                baltopProfiles = old;
            }
        }

        AtomicLong old = profile.balances().get(currency);
        if (old == null) {
            AtomicLong bal = new AtomicLong(DEFAULT_BALANCE);
            old = profile.balances().putIfAbsent(currency, bal);
            if (old == null) {
                old = bal; // No race condition
            }
        }

        baltopProfiles.remove(profile);
        old.getAndAdd(amount);
        baltopProfiles.add(profile);
        currency.circulation().addAndGet(amount);
    }

    private void currencyCreate(@NotNull String[] args, @NotNull CommandSender sender) {
        Locale lang = getLocale(sender);
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "You are not a player, however you need to be a player in order to create a currency.");
            return;
        }
        if (args.length != 2) {
            // Invalid syntax
            Component c = PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, lang), TextColor.color(255, 90, 90)))
                .append(Component.text("/ccreate ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(Translator.translate(Translator.REQUIRED_NAME, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC))
                .append(Component.space())
                .append(Component.text(Translator.translate(Translator.REQUIRED_ABBREVIATION, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC));
            sender.sendMessage(c);
            return;
        }

        if (currencyCreators.contains(((Player)sender).getUniqueId())) {
            Component c = PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.PLAYER_ALREADY_CREATED_CURRENCY, lang), NamedTextColor.RED));
            sender.sendMessage(c);
            return;
        }

        String name = args[0].toLowerCase(Locale.ENGLISH);
        String abbreviation = args[1].toUpperCase(Locale.ENGLISH);

        // Enforce name length limits
        if (abbreviation.length() != 3 || name.length() < 4) {
            sender.sendMessage(Component.text(Translator.translate(Translator.NAME_LENGTH_INVALID, lang), NamedTextColor.RED));
            return;
        }

        // Only allow characters in a whitelist
        // Why no regex you ask, well because the java gods demanded it
        boolean nameMismatch = name.toUpperCase(Locale.ENGLISH).codePoints().anyMatch(codepoint -> codepoint < 'A' || codepoint > 'Z');
        boolean abbrevMismatch = abbreviation.codePoints().anyMatch(codepoint -> codepoint < 'A' || codepoint > 'Z');
        if (nameMismatch || abbrevMismatch) {
            sender.sendMessage(Component.text(Translator.translate(Translator.ILLEGAL_CHARACTER, lang), NamedTextColor.RED));
            return;
        }

        if (nameToCurrency.containsKey(name)) {
            sender.sendMessage(Component.text(Translator.translate(Translator.NAME_PRESENT, lang), NamedTextColor.RED));
            return;
        }
        if (abbrevToCurrency.containsKey(abbreviation)) {
            sender.sendMessage(Component.text(Translator.translate(Translator.ABBREVIATION_PRESENT, lang), NamedTextColor.RED));
            return;
        }

        UUID id = UUID.randomUUID();
        if (id == null) {
            throw new InternalError();
        }
        if (currencies.containsKey(id)) {
            currencyCreate(args, sender); // just try again
            return;
        }
        Currency curr = new Currency(id, name, abbreviation, new AtomicLong(), new ArrayList<>(), new ArrayList<>());
        curr.headAdmins().add(((Player) sender).getUniqueId());
        currencies.put(id, curr);
        nameToCurrency.put(name, curr);
        abbrevToCurrency.put(abbreviation, curr);
        baltops.put(curr, getBaltopSortedSet(curr));
        currencyCreators.add(((Player) sender).getUniqueId());
        sender.sendMessage(Component.text(Translator.translate(Translator.CURRENCY_CREATED, lang), TextColor.color(150, 225, 100)));
    }

    private void currencyInfo(@NotNull String[] args, @NotNull CommandSender sender) {
        Locale lang = getLocale(sender);
        if (args.length != 1) {
            // Invalid syntax
            Component c = PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, lang), TextColor.color(255, 90, 90)))
                .append(Component.text("/cinfo ", NamedTextColor.AQUA))
                .append(Component.text(Translator.translate(Translator.REQUIRED_ABBREVIATION_OR_NAME, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC));
            sender.sendMessage(c);
            return;
        }

        Currency currency = getCurrency(args[0]);
        if (currency == null) {
            printUnknownCurrency(lang, sender, args[0]);
            return;
        }

        Component comp = Component.text(Translator.translate(Translator.CINFO_CURRENCY_NAME, lang), NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(currency.getName(), TextColor.color(130, 130, 210)));
        sender.sendMessage(comp);
        comp = Component.text(Translator.translate(Translator.CINFO_CURRENCY_ABBREV, lang), NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(currency.getAbbreviation(), TextColor.color(130, 130, 210)));
        sender.sendMessage(comp);
        comp = Component.text(Translator.translate(Translator.CINFO_CIRCULATION, lang), NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                .append(Component.text(currency.format(currency.circulation().get()), TextColor.color(130, 130, 210)));
        sender.sendMessage(comp);

        comp = Component.text().content(Translator.translate(Translator.CINFO_HEAD_ADMINS, lang))
                .color(NamedTextColor.DARK_GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(getTooltip(Translator.TOOLTIP_HEAD_ADMIN, lang))
                .build();
        comp = comp.append(joinPlayerNames(currency.getHeadAdmins()));
        sender.sendMessage(comp);

        comp = Component.text().content(Translator.translate(Translator.CINFO_ADMINS, lang))
                .color(NamedTextColor.DARK_GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(getTooltip(Translator.TOOLTIP_ADMIN, lang))
                .build();
        comp = comp.append(joinPlayerNames(currency.getAdmins(), currency.getHeadAdmins()));
        sender.sendMessage(comp);
    }

    private void currencyManage(@NotNull String[] args, @NotNull CommandSender sender) {
        Locale lang = getLocale(sender);
        if (args.length == 1 && !(args[0].equalsIgnoreCase("help") || args[0].equals("?"))) {
            sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, lang), TextColor.color(255, 90, 90))));
        }
        if (args.length == 0 || args.length == 1) {
            Translator.writeCurrencyManagementHelp("/cmanage ", sender, lang);
            return;
        }

        Currency currency = getCurrency(args[1]);
        if (currency == null) {
            printUnknownCurrency(lang, sender, args[1]);
            return;
        }

        switch (args[0]) {
        case "generate":
        case "print": // Valid, albeit undocumented alias for "generate"
            if (args.length != 3) {
                sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, getLocale(sender)), TextColor.color(255, 90, 90))));
                Component comp = PLUGIN_LOGO.append(Component.text("/cmanage ", HELP_COMMAND_COLOR));
                comp = comp.append(Translator.getHelpEntry(lang, Translator.CMANAGE_GENERATE_DESCRIPTION, Translator.CMANAGE_GENERATE_COMMAND, Translator.REQUIRED_ABBREVIATION_OR_NAME, Translator.REQUIRED_AMOUNT));
                sender.sendMessage(comp);
                return;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage("You need to be a player in order to do obtain money!");
                return;
            }
            UUID userUUID = ((OfflinePlayer) sender).getUniqueId();
            if (!currency.getAdmins().contains(userUUID) && !currency.getHeadAdmins().contains(userUUID)) {
                sender.sendMessage(Component.text(Translator.translate(Translator.INSUFFICENT_PERMISSION_ADMIN, lang), NamedTextColor.RED));
                return;
            }
            // /cmanage generate [abbreviation|name] [amount]
            long amount = 0;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                printInvalidAmount(sender, lang, args[2]);
                return;
            }
            if ((currency.circulation().get() + amount) >= (2L << 30)) {
                sender.sendMessage(Component.text(Translator.translate(Translator.CIRCULATION_TOO_HIGH, lang), NamedTextColor.RED));
                return;
            }
            if (amount < 0) {
                // Destroy money
                if (getBalance(userUUID, currency) < -amount) {
                    sender.sendMessage(Component.text(Translator.translate(Translator.NOT_ENOUGH_MONEY, lang), NamedTextColor.RED));
                } else {
                    addBalance(userUUID, currency, amount, "Destroyed money");
                    sender.sendMessage(Component.text(Translator.translate(Translator.CMANAGE_PRINT_SUCCESS, lang), NamedTextColor.GREEN));
                }
            } else {
                addBalance(userUUID, currency, amount, "Printed money");
                sender.sendMessage(Component.text(Translator.translate(Translator.CMANAGE_PRINT_SUCCESS, lang), NamedTextColor.GREEN));
            }
            break;
        case "appoint":
            sender.sendMessage("Not yet implemented, we will be implementing it soon however.");
            break;
        case "unappoint":
            sender.sendMessage("Not yet implemented, we will be implementing it soon however.");
            break;
        default:
            sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, getLocale(sender)), TextColor.color(255, 90, 90))));
            Translator.writeCurrencyManagementHelp("/cmanage ", sender, getLocale(sender));
        }
    }

    public long getBalance(@NotNull UUID player, @NotNull Currency currency) {
        Objects.requireNonNull(player, "Null player");
        Objects.requireNonNull(currency, "Null currency");
        PlayerProfile profile = balances.get(player);
        if (profile == null) {
            return 0L;
        }
        AtomicLong result = profile.balances().get(currency);
        if (result == null) {
            return DEFAULT_BALANCE;
        } else {
            return result.get();
        }
    }

    public ConcurrentSkipListSet<PlayerProfile> getBaltopSortedSet(Currency currency) {
        return new ConcurrentSkipListSet<>((o1, o2) -> {
            AtomicLong o1Balance = o1.balances().get(currency);
            AtomicLong o2Balance = o2.balances().get(currency);
            if (o1Balance == null) {
                if (o2Balance == null) {
                    return 0;
                }
                return Long.compare(0, o2Balance.get());
            } else if (o2Balance == null) {
                return Long.compare(o1Balance.get(), 0);
            } else {
                return Long.compare(o1Balance.get(), o2Balance.get());
            }
        });
    }

    @NotNull
    public Collection<Currency> getCurrencies() {
        Collection<Currency> c = Collections.unmodifiableCollection(currencies.values());
        if (c == null) {
            throw new InternalError();
        }
        return c;
    }

    @Nullable
    public Currency getCurrency(@NotNull String nameOrShorthand) {
        if (nameOrShorthand.length() == 3) {
            // Shorthand name. We can use this as it should be guaranteed that shorthand names are 3 characters and
            // full names are longer
            return abbrevToCurrency.get(nameOrShorthand.toUpperCase(Locale.ENGLISH));
        } else {
            // Full name
            return nameToCurrency.get(nameOrShorthand.toLowerCase(Locale.ENGLISH));
        }
    }

    @Nullable
    public Currency getCurrency(@NotNull UUID id) {
        return currencies.get(id);
    }

    @NotNull
    public Collection<String> getCurrencyNames() {
        Collection<String> c = Collections.unmodifiableCollection(nameToCurrency.keySet());
        if (c == null) {
            throw new InternalError();
        }
        return c;
    }

    @NotNull
    private Locale getLocale(@NotNull CommandSender sender) {
        if (sender instanceof Player p) {
            return p.locale();
        }
        return DEFAULT_LOCALE;
    }

    @SuppressWarnings("null")
    @NotNull
    public Collection<UUID> getPlayerUUIDs() {
        return Collections.unmodifiableCollection(balances.keySet());
    }

    @NotNull
    public PlayerProfile getProfile(@NotNull UUID player) {
        Objects.requireNonNull(player, "Null player");
        PlayerProfile profile = balances.get(player);
        if (profile == null) {
            profile = new PlayerProfile(player, new ConcurrentHashMap<>());
            PlayerProfile old = balances.putIfAbsent(player, profile);
            if (old != null) {
                profile = old; // Race condition :O
            }
        }
        return profile;
    }

    @NotNull
    private HoverEventSource<?> getTooltip(@NotNull String key, @NotNull Locale lang) {
        Component text = Component.text(Translator.translate(key, lang));
        return HoverEvent.showText(text);
    }

    @NotNull
    public TransferLog getTransferLog() {
        return log;
    }

    @SafeVarargs
    @NotNull
    private Component joinPlayerNames(@NotNull Iterable<@NotNull UUID>... players) {
        Component comp = Component.empty();
        boolean first = true;
        for (Iterable<@NotNull UUID> playerIter : players) {
            for (UUID playerUUID : playerIter) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(playerUUID);
                String name = p.getName();
                if (name == null) {
                    // I anticipate that this condition will be a pain to deal with
                    name = playerUUID.toString();
                    if (name == null) {
                        continue;
                    }
                }
                if (first) {
                    first = false;
                } else {
                    comp = comp.append(Component.text(", "));
                }
                comp = comp.append(Component.text(name, TextColor.color(195, 255, 195), TextDecoration.ITALIC));
            }
        }
        return comp;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName()) {
        case "cinfo":
            currencyInfo(args, sender);
            return true;
        case "ccreate":
            currencyCreate(args, sender);
            return true;
        case "cmanage":
            currencyManage(args, sender);
            return true;
        case "balance":
            printBalances(args, sender);
            return true;
        case "pay":
            processPayment(args, sender);
            return true;
        default:
            getLogger().warning("Unknown command: " + command.getName());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (!loadSuccess) {
            // Do not overwrite with corrupted data
            return;
        }
        try {
            File logfile = new File(getDataFolder(), "log.dat");
            log.write(new FileOutputStream(logfile));
            {
                File currencyStoreFile = new File(getDataFolder(), "currencies.dat");
                DataOutputStream out = new DataOutputStream(new FileOutputStream(currencyStoreFile));
                for (Currency currency : currencies.values()) {
                    out.write(1);
                    out.writeLong(currency.id().getMostSignificantBits());
                    out.writeLong(currency.id().getLeastSignificantBits());
                    out.writeUTF(currency.name());
                    out.writeUTF(currency.abbreviation());
                    out.writeLong(currency.circulation().get());
                    out.writeInt(currency.headAdmins().size());
                    for (UUID hadmin : currency.getHeadAdmins()) {
                        out.writeLong(hadmin.getMostSignificantBits());
                        out.writeLong(hadmin.getLeastSignificantBits());
                    }
                    out.writeInt(currency.admins().size());
                    for (UUID admin : currency.getAdmins()) {
                        out.writeLong(admin.getMostSignificantBits());
                        out.writeLong(admin.getLeastSignificantBits());
                    }
                }
                out.close();
            }
            {
                File balanceStoreFile = new File(getDataFolder(), "balances.dat");
                DataOutputStream out = new DataOutputStream(new FileOutputStream(balanceStoreFile));
                balances.forEach((uuid, player) -> {
                    try {
                        out.write(1);
                        out.writeLong(uuid.getMostSignificantBits());
                        out.writeLong(uuid.getLeastSignificantBits());
                        out.writeInt(player.balances().size());
                        player.balances().forEach((currency, amount) -> {
                            try {
                                out.writeLong(currency.id().getMostSignificantBits());
                                out.writeLong(currency.id().getLeastSignificantBits());
                                out.writeLong(amount.get());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to write balances.", e);
                    }
                });
                out.close();
            }
            {
                File currencyCreatorsFile = new File(getDataFolder(), "currency_creators.dat");
                try (FileOutputStream fileOut = new FileOutputStream(currencyCreatorsFile)) {
                    try (DataOutputStream out = new DataOutputStream(fileOut)) {
                        for (UUID id : currencyCreators) {
                            out.write(1);
                            out.writeLong(id.getMostSignificantBits());
                            out.writeLong(id.getLeastSignificantBits());
                        }
                    }
                }
            }
            centralOreIndicesListener.saveBrokenBlocksList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        try {
            CentralOreIndices notNullCentralOreIndicesListener = centralOreIndicesListener = new CentralOreIndices(this);
            Bukkit.getPluginManager().registerEvents(notNullCentralOreIndicesListener, this);
        } catch (Exception e) {
            loadSuccess = false;
            throw e;
        }
        PluginCommand baltopCmd = getCommand("balancetop");
        if (baltopCmd == null) {
            throw new IllegalStateException();
        }
        baltopCmd.setTabCompleter((sender, cmd, alias, args) -> {
            ArrayList<String> names = new ArrayList<>();
            getCurrencyNames().forEach(name -> {
                if (name.startsWith(args[0])) {
                    names.add(name);
                }
            });
            return names;
        });
        baltopCmd.setExecutor((sender, cmd, label, args) -> {
            Locale lang = getLocale(sender);
            if (args.length < 1) {
                Component c = PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, lang), TextColor.color(255, 90, 90)))
                        .append(Component.text("/" + label + " ", NamedTextColor.AQUA))
                        .append(Component.text(Translator.translate(Translator.REQUIRED_ABBREVIATION_OR_NAME, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC));
                sender.sendMessage(c);
                return true;
            }
            Currency currency = getCurrency(args[0]);
            if (currency == null) {
                printUnknownCurrency(lang, sender, args[0]);
                return true;
            }
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                NavigableSet<PlayerProfile> players = baltops.get(currency);
                sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.BALTOP_START, lang), NamedTextColor.DARK_GREEN)));
                if (players == null) {
                    sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.BALTOP_END, lang), NamedTextColor.DARK_GREEN)));
                    return;
                }
                players = players.descendingSet();
                int start = 0;
                int end = 12 + start;
                int i = 0;
                int maxIndexCharCount = Integer.toString(end + 1).length();
                for (PlayerProfile profile : players) {
                    if (i >= start) {
                        if (i >= end) {
                            break;
                        }
                        AtomicLong balance = profile.balances().get(currency);
                        if (balance == null) {
                            continue;
                        }
                        UUID uuid = profile.player();
                        uuid = Objects.requireNonNull(uuid);
                        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                        if (!player.isOnline() && !player.hasPlayedBefore()) {
                            continue;
                        }
                        String name = player.getName();
                        if (name == null) {
                            continue;
                        }

                        StringBuilder indexString = new StringBuilder(maxIndexCharCount + 2);
                        String indexStringNum = Integer.toString(i + 1);
                        int padCharCount = maxIndexCharCount - indexStringNum.length();
                        while (padCharCount-- > 0) {
                            indexString.append(' ');
                        }
                        indexString.append(indexStringNum);
                        indexString.append(". ");

                        sender.sendMessage(Component.text(indexString.toString(), NamedTextColor.WHITE)
                                .append(Component.text(name, NamedTextColor.GREEN))
                                .append(Component.text(": ", NamedTextColor.GOLD))
                                .append(Component.text(balance.longValue())));
                    }
                    i++;
                }
                sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.BALTOP_END, lang), NamedTextColor.DARK_GREEN)));
            });
            return true;
        });
    }

    @Override
    public void onLoad() {
        getDataFolder().mkdirs(); // This is an issue with bukkit, I do not know why they choose to not create the data folder themselves, but eh
        PlayerCurrency.instance = this;
        try {
            File logfile = new File(getDataFolder(), "log.dat");
            logfile.createNewFile();
            log.read(new FileInputStream(logfile));
            readCurrencies: {
                File currencyStoreFile = new File(getDataFolder(), "currencies.dat");
                if (!currencyStoreFile.exists()) {
                     break readCurrencies; // Can't read what does not exist
                }
                try (DataInputStream in = new DataInputStream(new FileInputStream(currencyStoreFile))) {
                    while (in.read() > 0) {
                        UUID id = new UUID(in.readLong(), in.readLong());
                        String name = in.readUTF();
                        String shorthand = in.readUTF();
                        AtomicLong circulation = new AtomicLong(in.readLong());
                        Collection<UUID> headAdmins = new ArrayList<>();
                        Collection<UUID> admins = new ArrayList<>();
                        for (int i = in.readInt(); i > 0; i--) {
                            headAdmins.add(new UUID(in.readLong(), in.readLong()));
                        }
                        for (int i = in.readInt(); i > 0; i--) {
                            admins.add(new UUID(in.readLong(), in.readLong()));
                        }
                        if (name == null || shorthand == null || name.length() < 4 || shorthand.length() != 3) {
                            throw new IllegalStateException("Invalid name or shorthand.");
                        }
                        Currency currency = new Currency(id, name, shorthand, circulation, headAdmins, admins);
                        currencies.put(id, currency);
                        nameToCurrency.put(name.toLowerCase(Locale.ENGLISH), currency);
                        abbrevToCurrency.put(shorthand.toUpperCase(Locale.ENGLISH), currency);
                        baltops.put(currency, getBaltopSortedSet(currency));
                    }
                }
            }
            if (!currencies.containsKey(CENTRAL_COPPER_INDEX)) {
                @SuppressWarnings("null")
                Currency copper = new Currency(CENTRAL_COPPER_INDEX, "copper", "CCI", new AtomicLong(),
                        Collections.emptySet(), Collections.emptySet());
                @SuppressWarnings("null")
                Currency iron = new Currency(CENTRAL_IRON_INDEX, "iron", "CII", new AtomicLong(),
                        Collections.emptySet(), Collections.emptySet());
                @SuppressWarnings("null")
                Currency diamond = new Currency(CENTRAL_DIAMOND_INDEX, "diamond", "CDI", new AtomicLong(),
                        Collections.emptySet(), Collections.emptySet());
                currencies.put(CENTRAL_COPPER_INDEX, copper);
                currencies.put(CENTRAL_IRON_INDEX, iron);
                currencies.put(CENTRAL_DIAMOND_INDEX, diamond);
                abbrevToCurrency.put(copper.abbreviation(), copper);
                abbrevToCurrency.put(iron.abbreviation(), iron);
                abbrevToCurrency.put(diamond.abbreviation(), diamond);
                nameToCurrency.put(copper.name(), copper);
                nameToCurrency.put(iron.name(), iron);
                nameToCurrency.put(diamond.name(), diamond);
            }
            readBalances: {
                File balanceStoreFile = new File(getDataFolder(), "balances.dat");
                if (!balanceStoreFile.exists()) {
                     break readBalances; // Can't read what does not exist
                }
                try (DataInputStream in = new DataInputStream(new FileInputStream(balanceStoreFile))) {
                    while (in.read() > 0) {
                        UUID id = new UUID(in.readLong(), in.readLong());
                        Map<Currency, AtomicLong> balances = new ConcurrentHashMap<>();
                        PlayerProfile profile = new PlayerProfile(id, balances);
                        for (int i = in.readInt(); i > 0; i--) {
                            Currency currency = currencies.get(new UUID(in.readLong(), in.readLong()));
                            long balance = in.readLong();
                            balances.put(currency, new AtomicLong(balance));
                            this.baltops.get(currency).add(profile);
                        }
                        this.balances.put(id, profile);
                    }
                }
            }
            readCurrencyCreators: {
                File currencyCreatorsFile = new File(getDataFolder(), "currency_creators.dat");
                if (!currencyCreatorsFile.exists()) {
                     break readCurrencyCreators; // Can't read what does not exist
                }
                try (DataInputStream in = new DataInputStream(new FileInputStream(currencyCreatorsFile))) {
                    while (in.read() > 0) {
                        currencyCreators.add(new UUID(in.readLong(), in.readLong()));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load plugin data", e);
        }
        loadSuccess = true;
        try {
            Class.forName("me.lokka30.treasury.api.economy.EconomyProvider", false, getClassLoader());
            @SuppressWarnings("unchecked")
            Class<BasicInterop> c = (Class<BasicInterop>) getClassLoader().loadClass("de.geolykt.playercurrency.interop.TreasuryBridge");
            c.getDeclaredConstructor().newInstance().onLoad(this);
        } catch (Throwable ignored) {}
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender,
            @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        switch (command.getName()) {
        case "cinfo": {
            ArrayList<String> names = new ArrayList<>();
            getCurrencyNames().forEach(name -> {
                if (name.startsWith(args[0])) {
                    names.add(name);
                }
            });
            return names;
        }
        case "ccreate":
            return Collections.emptyList();
        case "cmanage": {
            ArrayList<String> suggestions = new ArrayList<>();
            if ("help".startsWith(args[0])) {
                suggestions.add("help");
            }
            if ("print".startsWith(args[0])) {
                suggestions.add("print");
            }
            return suggestions;
        }
        case "balance":
            return null;
        case "pay":
            if (args.length == 1) {
                return null;
            }
            if (args.length == 2) {
                ArrayList<String> names = new ArrayList<>();
                getCurrencyNames().forEach(name -> {
                    if (name.startsWith(args[1])) {
                        names.add(name);
                    }
                });
                return names;
            }
            if (args.length == 3) {
                return Collections.emptyList();
            }
            return null;
        }
        return super.onTabComplete(sender, command, alias, args);
    }

    private void printBalances(@NotNull String[] args, @NotNull CommandSender sender) {
        PlayerProfile profile;
        if (args.length == 0 && sender instanceof Player p) {
            profile = balances.get(p.getUniqueId());
        } else if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "You are not a player and therefore you cannot get your own balances.");
            return;
        } else {
            OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (player == null) {
                sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.PLAYER_UNKNOWN, getLocale(sender)), NamedTextColor.RED)));
                return;
            }
            profile = balances.get(player.getUniqueId());
        }

        if (profile == null) {
            sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.NO_BALANCES, getLocale(sender)), NamedTextColor.GOLD)));
            return;
        }

        sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.BALANCES_START, getLocale(sender)), NamedTextColor.GOLD)));

        Style balanceStyle = Style.style(NamedTextColor.GREEN);
        profile.balances().forEach((currency, balance) -> {
            sender.sendMessage(PLUGIN_LOGO.append(
                    Component.text(currency.getName(), NamedTextColor.YELLOW)
                    .append(Component.text(" [", NamedTextColor.WHITE))
                    .append(Component.text(currency.getAbbreviation(), NamedTextColor.GOLD))
                    .append(Component.text("]: ", NamedTextColor.WHITE))
                    .append(Component.text(balance.longValue(), balanceStyle))));
        });

        sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.BALANCES_END, getLocale(sender)), NamedTextColor.GOLD)));
    }

    private void printInvalidAmount(@NotNull Audience sink, @NotNull Locale lang, @NotNull String invalidAmount) {
        Component c = Component.text(Translator.translate(Translator.ERROR_INVALID_AMOUNT, lang), NamedTextColor.RED)
                .append(Component.text(invalidAmount, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        sink.sendMessage(c);
    }

    private void printUnknownCurrency(@NotNull Locale lang, @NotNull Audience sink, @NotNull String unknownCurrency) {
        Component c = Component.text(Translator.translate(Translator.CURRENCY_NOT_FOUND, lang), NamedTextColor.RED)
                .append(Component.text(unknownCurrency, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        sink.sendMessage(c);
    }

    private void processPayment(@NotNull String[] args, @NotNull CommandSender sender) {
        UUID source;
        if (sender instanceof Player p) {
            source = p.getUniqueId();
        } else {
            sender.sendMessage(ChatColor.RED + "You must be a player to perform this action.");
            return;
        }
        Locale lang = getLocale(sender);
        if (args.length != 3) {
            Component c = PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, lang), TextColor.color(255, 90, 90)))
                .append(Component.text("/pay ", NamedTextColor.AQUA))
                .append(Component.text(Translator.translate(Translator.REQUIRED_PLAYER, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC))
                .append(Component.space())
                .append(Component.text(Translator.translate(Translator.REQUIRED_CURRENCY, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC))
                .append(Component.space())
                .append(Component.text(Translator.translate(Translator.REQUIRED_AMOUNT, lang), NamedTextColor.RED, TextDecoration.BOLD, TextDecoration.ITALIC));
            sender.sendMessage(c);
            return;
        }

        // /pay [player] [currency] [amount]
        Currency currency = getCurrency(args[1]);
        if (currency == null) {
            printUnknownCurrency(lang, sender, args[1]);
            return;
        }

        long amount = 0;
        try {
            amount = Long.parseUnsignedLong(args[2]);
        } catch (NumberFormatException ex) {
            printInvalidAmount(sender, lang, args[2]);
            return;
        }

        // Do not allow sending no money
        if (amount == 0) {
            printInvalidAmount(sender, lang, args[2]);
            return;
        }

        // Make sure that the sender has the money
        if (getBalance(source, currency) < amount) {
            sender.sendMessage(Component.text(Translator.translate(Translator.NOT_ENOUGH_MONEY, lang), NamedTextColor.RED));
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.PLAYER_UNKNOWN, getLocale(sender)), NamedTextColor.RED)));
            return;
        }

        addBalance(source, currency, -amount, "Transfer of money initiated via the /pay command");
        addBalance(targetPlayer.getUniqueId(), currency, amount, "Transfer of money initiated via the /pay command");

        sender.sendMessage(PLUGIN_LOGO.append(Component.text(Translator.translate(Translator.PAY_TRANSACTION_SUCCESS, getLocale(sender)), NamedTextColor.GREEN)));

        if (targetPlayer.getPlayer() != null) {
            String sourceName = p.getName(); // Yeah, apparently we can refer to `p` here, let's see how quick that falls apart
            String formatString = Translator.translate(Translator.RECIEVED_MONEY, getLocale(sender));
            String formatted = formatString.formatted(sourceName, amount, currency.getAbbreviation());
            if (formatted == null) {
                throw new NullPointerException("String#formatted returned a null String???");
            }
            sender.sendMessage(PLUGIN_LOGO.append(Component.text(formatted, NamedTextColor.GREEN)));
        }
    }

    public void setBalance(@NotNull UUID player, @NotNull Currency currency, long amount, @NotNull String reasoning) {
        Objects.requireNonNull(reasoning, "Null reasoning");
        PlayerProfile profile = getProfile(player);

        ConcurrentSkipListSet<PlayerProfile> baltopProfiles = baltops.get(currency);
        if (baltopProfiles == null) {
            baltopProfiles = getBaltopSortedSet(currency);
            ConcurrentSkipListSet<PlayerProfile> old = baltops.putIfAbsent(currency, baltopProfiles);
            if (old != null) { // race condition :O
                baltopProfiles = old;
            }
        }

        AtomicLong old = profile.balances().get(currency);
        if (old == null) {
            AtomicLong bal = new AtomicLong(DEFAULT_BALANCE);
            old = profile.balances().putIfAbsent(currency, bal);
            if (old == null) {
                old = bal; // No race condition
            }
        }

        baltopProfiles.remove(profile);
        long oldAmount = old.getAndSet(amount);
        baltopProfiles.add(profile);
        currency.circulation().addAndGet(amount - oldAmount);
        log.pushLog(player, currency, amount - oldAmount, reasoning);
    }
}
