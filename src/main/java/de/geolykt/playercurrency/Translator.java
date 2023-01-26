package de.geolykt.playercurrency;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class Translator {

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> DE = new HashMap<>();
    private static final Map<String, String> DEFAULT = EN;

    @NotNull
    public static final String WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX = "wrong-arg-count";
    @NotNull
    public static final String REQUIRED_ABBREVIATION_OR_NAME = "required.abbrev-or-name";
    @NotNull
    public static final String CURRENCY_NOT_FOUND = "currency-not-found";
    @NotNull
    public static final String CINFO_CURRENCY_NAME = "cinfo.name";
    @NotNull
    public static final String CINFO_CURRENCY_ABBREV = "cinfo.abbrev";
    @NotNull
    public static final String CINFO_CIRCULATION = "cinfo.circulation";
    @NotNull
    public static final String CINFO_HEAD_ADMINS = "cinfo.hadmin";
    @NotNull
    public static final String CINFO_ADMINS = "cinfo.admin";
    @NotNull
    public static final String TOOLTIP_HEAD_ADMIN = "tooltip.hadmin";
    @NotNull
    public static final String TOOLTIP_ADMIN = "tooltip.admin";
    @NotNull
    public static final String REQUIRED_ABBREVIATION = "require.abbrev";
    @NotNull
    public static final String REQUIRED_NAME = "require.name";
    @NotNull
    public static final String ABBREVIATION_PRESENT = "abbrev.present";
    @NotNull
    public static final String NAME_PRESENT = "name.present";
    @NotNull
    public static final String CURRENCY_CREATED = "currency.created.success";
    @NotNull
    public static final String PLAYER_ALREADY_CREATED_CURRENCY = "currency.created.failed.already-created-a-currency";
    @NotNull
    public static final String REQUIRED_AMOUNT = "require.amount";
    @NotNull
    public static final String CMANAGE_GENERATE_COMMAND = "cmanage.generate.cmd";
    @NotNull
    public static final String CMANAGE_GENERATE_DESCRIPTION = "cmanage.generate.desc";
    @NotNull
    public static final String REQUIRED_PLAYER = "require.player";
    @NotNull
    public static final String CMANAGE_APPOINT_COMMAND = "cmanage.appoint.cmd";
    @NotNull
    public static final String CMANAGE_APPOINT_DESCRIPTION = "cmanage.appoint.desc";
    @NotNull
    public static final String REQUIRED_LITERAL_ADMIN_OR_HEADADMIN = "require.literal.admin-hadmin";
    @NotNull
    public static final String CMANAGE_UNAPPOINT_COMMAND = "cmanage.unappoint.cmd";
    @NotNull
    public static final String CMANAGE_UNAPPOINT_DESCRIPTION = "cmanage.unappoint.desc";
    @NotNull
    public static final String ERROR_INVALID_AMOUNT = "error.invalid-amount";
    @NotNull
    public static final String CIRCULATION_TOO_HIGH = "error.circulation-too-high";
    @NotNull
    public static final String NOT_ENOUGH_MONEY = "error.not-enough-money";
    @NotNull
    public static final String INSUFFICENT_PERMISSION_ADMIN = "perm.admin.missing";
    @NotNull
    public static final String NAME_LENGTH_INVALID = "error.param.abbrev-or-name.length";
    @NotNull
    public static final String ILLEGAL_CHARACTER = "error.param.abbrev-or-name.illegalchar";
    @NotNull
    public static final String PLAYER_UNKNOWN = "error.param.player.unknown";
    @NotNull
    public static final String NO_BALANCES = "balances.none";
    @NotNull
    public static final String BALANCES_START = "balances.start";
    @NotNull
    public static final String BALANCES_END = "balances.end";
    @NotNull
    public static final String CMANAGE_PRINT_SUCCESS = "cmanage.generate.success";
    @NotNull
    public static final String REQUIRED_CURRENCY = "required.curr";
    @NotNull
    public static final String PAY_TRANSACTION_SUCCESS = "pay.send";
    @NotNull
    public static final String RECIEVED_MONEY = "pay.recieve";
    @NotNull
    public static final String BALTOP_START = "baltop.start";
    @NotNull
    public static final String BALTOP_END = "baltop.end";

    @NotNull
    public static String translate(@NotNull String key, @NotNull Locale locale) {
        Map<String, String> translationTable = TRANSLATIONS.getOrDefault(locale.getISO3Language(), DEFAULT);
        String value = translationTable.get(key);
        if (value == null) {
            PlayerCurrency.getInstance().getSLF4JLogger().warn("Unable to find value for translation key \"{}\" in language table \"{}\".", key, locale.getISO3Language());
            String s = DEFAULT.getOrDefault(key, key);
            if (s == null) {
                return "";
            }
            return s;
        }
        return value;
    }

    static {
        TRANSLATIONS.put(Locale.ENGLISH.getISO3Language(), EN);
        TRANSLATIONS.put(Locale.GERMAN.getISO3Language(), DE);

        EN.put(WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, "Syntax error: Wrong argument count. Correct command syntax: ");
        DE.put(WRONG_ARGUMENT_COUNT_CORRECTED_SYNTAX, "Falsche Anzahl von Befehlsparametern. Richtige syntax: ");

        EN.put(REQUIRED_ABBREVIATION_OR_NAME, "[abbreviation|name]");
        DE.put(REQUIRED_ABBREVIATION_OR_NAME, "[Kürzel|Name]");

        EN.put(CURRENCY_NOT_FOUND, "Currency not found (Did you perform any typographical errors?): ");
        DE.put(CURRENCY_NOT_FOUND, "Währung konnte nicht gefunden werden: ");

        EN.put(CINFO_CURRENCY_NAME, "Name: ");
        DE.put(CINFO_CURRENCY_NAME, "Name: ");

        EN.put(CINFO_CURRENCY_ABBREV, "Abbreviation: ");
        DE.put(CINFO_CURRENCY_ABBREV, "Kürzel: ");

        EN.put(CINFO_CIRCULATION, "Currency in circulation: ");
        DE.put(CINFO_CIRCULATION, "Im Umlauf sind: ");

        EN.put(CINFO_HEAD_ADMINS, "Head administrators: ");
        DE.put(CINFO_HEAD_ADMINS, "Administrationsleiter: ");

        EN.put(CINFO_ADMINS, "Administrators: ");
        DE.put(CINFO_ADMINS, "Administratoren: ");

        EN.put(TOOLTIP_ADMIN, "Administrators can create more units of this currency.");
        DE.put(TOOLTIP_ADMIN, "Administratoren können mehr von dieser Währung prägen.");

        EN.put(TOOLTIP_HEAD_ADMIN, "Head Administrators can appoint (head) administrators. They can also remove (head) administrators");
        DE.put(TOOLTIP_HEAD_ADMIN, "Administrationsleiter können neue Administratoren und Administrationsleiter benennen. Sie können auch Administratoren und Administrationsleiter entferen.");

        EN.put(REQUIRED_ABBREVIATION, "[abbreviation]");
        DE.put(REQUIRED_ABBREVIATION, "[Kürzel]");

        EN.put(REQUIRED_NAME, "[name]");
        DE.put(REQUIRED_NAME, "[Name]");

        EN.put(ABBREVIATION_PRESENT, "There is already a currency with this abbreviation.");
        DE.put(ABBREVIATION_PRESENT, "Eine Währung hat bereits dieses Kürzel.");

        EN.put(NAME_PRESENT, "There is already a currency going by this name.");
        DE.put(NAME_PRESENT, "Es gibt schon eine Währung mit diesen Namen.");

        EN.put(CURRENCY_CREATED, "Successfully created your currency.");
        DE.put(CURRENCY_CREATED, "Währung erfolgreich erstellt.");

        EN.put(REQUIRED_AMOUNT, "[amount]");
        DE.put(REQUIRED_AMOUNT, "[Menge]");

        EN.put(CMANAGE_GENERATE_COMMAND, "generate");
        DE.put(CMANAGE_GENERATE_COMMAND, "generate"); // We will eventually have locale-specific sub-commands, but right now that is unsupported

        EN.put(CMANAGE_GENERATE_DESCRIPTION, "Generates a batch of money of the given currency that you will own afterwards.");
        DE.put(CMANAGE_GENERATE_DESCRIPTION, "Prägt eine bestimmte Menge einer Währung.");

        EN.put(REQUIRED_PLAYER, "[player]");
        DE.put(REQUIRED_PLAYER, "[Spieler]");

        EN.put(CMANAGE_APPOINT_COMMAND, "appoint");
        DE.put(CMANAGE_APPOINT_COMMAND, "appoint"); // Same here

        EN.put(CMANAGE_APPOINT_DESCRIPTION, "Appoint a player to be a (head) administrator of a currency.");
        DE.put(CMANAGE_APPOINT_DESCRIPTION, "Benenne ein Administrator oder Administrationsleiter."); // Does not sound right

        EN.put(REQUIRED_LITERAL_ADMIN_OR_HEADADMIN, "headadmin|admin");
        DE.put(REQUIRED_LITERAL_ADMIN_OR_HEADADMIN, "headadmin|admin"); // Maybe allow for this to be translatable too

        EN.put(CMANAGE_UNAPPOINT_COMMAND, "unappoint");
        DE.put(CMANAGE_UNAPPOINT_COMMAND, "unappoint"); // And this too

        EN.put(CMANAGE_UNAPPOINT_DESCRIPTION, "Remove (head) administrator of a currency.");
        DE.put(CMANAGE_UNAPPOINT_DESCRIPTION, "Entferne ein Administrator oder Administrationsleiter einer Währung.");

        EN.put(ERROR_INVALID_AMOUNT, "Could not parse your amount. Note that only interger amounts are allowed. You used: ");
        DE.put(ERROR_INVALID_AMOUNT, "Ungültige Menge. Beachte, dass nur Ganzzahlen gültig sind. Verwendet wurde: ");

        EN.put(CIRCULATION_TOO_HIGH, "There is already too much of that currency in circulation. Consider destroying a bit");
        DE.put(CIRCULATION_TOO_HIGH, "Es gibt zu viel von dieser Währung im Umlauf. Vielleicht ist etwas Zerstörung angesagt?");

        EN.put(NOT_ENOUGH_MONEY, "You do not have enough money in your bank to complete this transaction.");
        DE.put(NOT_ENOUGH_MONEY, "Es gibt nicht genug Geld in der Kasse, um diese Transaktion zu gewährleisten.");

        EN.put(INSUFFICENT_PERMISSION_ADMIN, "You must be a (head) administrator of this currency in order to do this.");
        DE.put(INSUFFICENT_PERMISSION_ADMIN, "Nur ein Administrator oder ein Administrationsleiter dieser Währung darf dies tun.");

        EN.put(NAME_LENGTH_INVALID, "The name of a currency must be over 3 letters long. And the abbreviation must be equal to 3 letters long.");
        DE.put(NAME_LENGTH_INVALID, "Der Kürzel einer Währung muss 3 Zeichen lang sein, der Name länger.");

        EN.put(ILLEGAL_CHARACTER, "The name or abbreviation of the currency contains an illegal character. Consider using standard english letters.");
        DE.put(ILLEGAL_CHARACTER, "Der Name oder Kürzel der Wärhung enthält ein ungültiges Zeichen.");

        EN.put(PLAYER_UNKNOWN, "The requested player has never played on this server. Perhaps you did a typo?");
        DE.put(PLAYER_UNKNOWN, "Dieser Spieler hat sich nie in diesem Server eingeloggt.");

        EN.put(NO_BALANCES, "This player has no money at all.");
        DE.put(NO_BALANCES, "Dieser Spieler hat gar kein Geld.");

        EN.put(BALANCES_START, "Balances start");
        DE.put(BALANCES_START, "Guthaben Start");

        EN.put(BALANCES_END, "Balances end");
        DE.put(BALANCES_END, "Guthaben Ende");

        EN.put(CMANAGE_PRINT_SUCCESS, "Successfully printed the money. It was transfered to your balance.");
        DE.put(CMANAGE_PRINT_SUCCESS, "Das Geld wurde Erfolgreich gedruckt und wurde auf das Konto übertragen.");

        EN.put(REQUIRED_CURRENCY, "[currency]");
        DE.put(REQUIRED_CURRENCY, "[Währung]");

        EN.put(PAY_TRANSACTION_SUCCESS, "Transaction successfull.");
        DE.put(PAY_TRANSACTION_SUCCESS, "Transaktion erfolgreich.");

        // This string is formatted, see https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/util/Formatter.html
        // index 1 = sender name
        // index 2 = amount
        // index 3 = currency shorthand
        EN.put(RECIEVED_MONEY, "You recieved %2$d %3$s from %1$s");
        DE.put(RECIEVED_MONEY, "%2$d %3$s wurde von %1$s auf das Konto übertragen.");

        EN.put(BALTOP_START, "Baltop start");
        DE.put(BALTOP_START, "Start der Bestenliste");

        EN.put(BALTOP_END, "Baltop end");
        DE.put(BALTOP_END, "Ende der Bestenliste");

        EN.put(PLAYER_ALREADY_CREATED_CURRENCY, "You already created a currency. You may not create another.");
        DE.put(PLAYER_ALREADY_CREATED_CURRENCY, "Sie haben bereits eine Währung erstellt. Sie können keine Weitere erstellen");
    }

    @NotNull
    static Component getHelpEntry(@NotNull Locale language, @NotNull String descKey, @NotNull String... argKeys) {
        Component c = Component.empty();
        for (String argKey : argKeys) {
            c = c.append(Component.space());
            c = c.append(Component.text(translate(argKey, language), PlayerCurrency.HELP_ARGUMENT_COLOR, TextDecoration.BOLD));
        }
        return c.append(Component.text(": ")).append(Component.text(translate(descKey, language), NamedTextColor.DARK_GREEN));
    }

    static void writeCurrencyManagementHelp(@NotNull String base, @NotNull Audience sink, @NotNull Locale language) {
        Component baseComponent = Component.text(base, PlayerCurrency.HELP_COMMAND_COLOR);

        // /cmanage generate [abbreviation|name] [amount]
        sink.sendMessage(baseComponent.append(getHelpEntry(language, CMANAGE_GENERATE_DESCRIPTION, CMANAGE_GENERATE_COMMAND, REQUIRED_ABBREVIATION_OR_NAME, REQUIRED_AMOUNT)));

        // /cmanage appoint [abbreviation|name] admin|headadmin [player]
        sink.sendMessage(baseComponent.append(getHelpEntry(language, CMANAGE_APPOINT_DESCRIPTION, CMANAGE_APPOINT_COMMAND, REQUIRED_ABBREVIATION_OR_NAME, REQUIRED_LITERAL_ADMIN_OR_HEADADMIN, REQUIRED_PLAYER)));

        // /cmanage unappoint [abbreviation|name] admin|headadmin [player]
        sink.sendMessage(baseComponent.append(getHelpEntry(language, CMANAGE_UNAPPOINT_DESCRIPTION, CMANAGE_UNAPPOINT_COMMAND, REQUIRED_ABBREVIATION_OR_NAME, REQUIRED_LITERAL_ADMIN_OR_HEADADMIN, REQUIRED_PLAYER)));
    }
}
