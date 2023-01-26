package de.geolykt.playercurrency;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

public record Currency(@NotNull UUID id, @NotNull String name, @NotNull String abbreviation,
        @NotNull AtomicLong circulation, @NotNull Collection<UUID> headAdmins, @NotNull Collection<UUID> admins) {

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getAbbreviation() {
        return abbreviation;
    }

    @NotNull
    public String format(long amount) {
        return amount + " " + abbreviation;
    }

    @SuppressWarnings("null")
    @NotNull
    public Collection<@NotNull UUID> getHeadAdmins() {
        return headAdmins;
    }

    @SuppressWarnings("null")
    @NotNull
    public Collection<@NotNull UUID> getAdmins() {
        return admins;
    }
}
