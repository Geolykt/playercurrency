package de.geolykt.playercurrency;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

public record PlayerProfile(@NotNull UUID player, @NotNull Map<Currency, AtomicLong> balances) { }
