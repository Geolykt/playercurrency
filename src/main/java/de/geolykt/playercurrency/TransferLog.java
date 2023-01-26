package de.geolykt.playercurrency;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Iterators;

public class TransferLog {

    private final Queue<TransferLogEntry> logs = new ConcurrentLinkedQueue<>();

    public void read(@NotNull InputStream in) throws IOException {
        logs.clear();
        DataInputStream dataIn = new DataInputStream(in);
        while (dataIn.read() > 0) {
            try {
                long uuidMostSig = dataIn.readLong();
                long uuidLeastSig = dataIn.readLong();
                String currency = dataIn.readUTF();
                if (currency.length() != 3) {
                    throw new IllegalStateException("Illegal currency shorthand. Read: " + currency);
                }
                long amount = dataIn.readLong();
                String reason = dataIn.readUTF();
                if (reason == null) {
                    throw new IllegalStateException("Null reason.");
                }
                long timestamp = dataIn.readLong();
                logs.add(new TransferLogEntry(new UUID(uuidMostSig, uuidLeastSig), currency, amount, reason, timestamp));
            } catch (EOFException ex) {
                ex.printStackTrace();
                break;
            }
        }
    }

    public void write(@NotNull OutputStream out) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        for (TransferLogEntry entry : logs) {
            dataOut.write(1);
            dataOut.writeLong(entry.user().getMostSignificantBits());
            dataOut.writeLong(entry.user().getLeastSignificantBits());
            dataOut.writeUTF(entry.currency());
            dataOut.writeLong(entry.amount());
            dataOut.writeUTF(entry.reason());
            dataOut.writeLong(entry.timestamp());
        }
    }

    public void pushLog(@NotNull UUID user, @NotNull Currency currency, long amount, @NotNull String reason) {
        logs.add(new TransferLogEntry(user, currency.getAbbreviation(), amount, reason, System.currentTimeMillis()));
    }

    public void pushLog(@NotNull UUID user, @NotNull Currency currency, long amount, @NotNull String reason, long timestamp) {
        logs.add(new TransferLogEntry(user, currency.getAbbreviation(), amount, reason, timestamp));
    }

    public Iterator<TransferLogEntry> getLogs() {
        return Iterators.unmodifiableIterator(logs.iterator());
    }

    public static record TransferLogEntry(@NotNull UUID user, @NotNull String currency, long amount, @NotNull String reason, long timestamp) {}
}
