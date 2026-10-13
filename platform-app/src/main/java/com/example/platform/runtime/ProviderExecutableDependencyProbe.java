package com.example.platform.runtime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * P1-1A.6 (Gate 3): the minimal, explainable runtime-dependency check for the bounded-V1 provider
 * executable.
 *
 * <p>It inspects the exact configured executable's ELF program headers and reports whether the
 * artifact carries a dynamic section ({@code PT_DYNAMIC}, i.e. {@code DT_NEEDED} shared-library
 * dependencies). A statically linked executable has no external shared-library runtime dependency,
 * so the runtime dependency observation may be published as a <b>verified empty</b> set.
 *
 * <p><b>What it checks:</b> that the exact executable the worker runs is statically self-contained.
 * <b>What it does not check:</b> dynamically loaded ({@code dlopen}) libraries, data/config files,
 * or kernel/runtime (glibc) capabilities. Anything it cannot determine yields
 * {@link Status#UNKNOWN} (fail closed), never a fabricated empty set.
 */
public final class ProviderExecutableDependencyProbe {

    private static final int ELF_CLASS_32 = 1;
    private static final int ELF_CLASS_64 = 2;
    private static final int ELF_DATA_LSB = 1;
    private static final int PT_DYNAMIC = 2;
    private static final int MAX_PROGRAM_HEADERS = 4096;

    public Result probe(Path executable) {
        Objects.requireNonNull(executable, "executable");
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            return new Result(Status.UNKNOWN, executable + " is not an executable file");
        }
        try (RandomAccessFile file = new RandomAccessFile(executable.toFile(), "r")) {
            byte[] header = new byte[64];
            if (file.read(header) < 64 || header[0] != 0x7F
                    || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
                return new Result(Status.UNKNOWN, executable + " is not an ELF artifact");
            }
            int elfClass = header[4];
            int data = header[5];
            if (data != ELF_DATA_LSB) {
                return new Result(Status.UNKNOWN, executable + " is not little-endian ELF");
            }
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            long programHeaderOffset;
            int programHeaderSize;
            int programHeaderCount;
            if (elfClass == ELF_CLASS_64) {
                programHeaderOffset = buffer.getLong(0x20);
                programHeaderSize = Short.toUnsignedInt(buffer.getShort(0x36));
                programHeaderCount = Short.toUnsignedInt(buffer.getShort(0x38));
            } else if (elfClass == ELF_CLASS_32) {
                programHeaderOffset = Integer.toUnsignedLong(buffer.getInt(0x1C));
                programHeaderSize = Short.toUnsignedInt(buffer.getShort(0x2A));
                programHeaderCount = Short.toUnsignedInt(buffer.getShort(0x2C));
            } else {
                return new Result(Status.UNKNOWN, executable + " has an unknown ELF class");
            }
            if (programHeaderCount == 0
                    || programHeaderCount > MAX_PROGRAM_HEADERS
                    || programHeaderSize < 4
                    || programHeaderOffset <= 0) {
                return new Result(
                        Status.UNKNOWN, executable + " has an unreadable program header table");
            }
            file.seek(programHeaderOffset);
            byte[] table = new byte[programHeaderSize * programHeaderCount];
            if (file.read(table) < table.length) {
                return new Result(Status.UNKNOWN, executable + " program header table is truncated");
            }
            ByteBuffer headers = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN);
            for (int index = 0; index < programHeaderCount; index++) {
                int type = headers.getInt(index * programHeaderSize);
                if (type == PT_DYNAMIC) {
                    return new Result(
                            Status.DYNAMIC_DEPENDENCIES_PRESENT,
                            executable + " declares a dynamic section (external libraries required)");
                }
            }
            return new Result(
                    Status.STATIC_SELF_CONTAINED,
                    executable + " has no dynamic section (no external library dependency)");
        } catch (IOException | RuntimeException failure) {
            return new Result(Status.UNKNOWN,
                    executable + " dependency probe failed: " + failure.getClass().getSimpleName());
        }
    }

    public enum Status {
        /** The executable is statically self-contained: a verified empty dependency set. */
        STATIC_SELF_CONTAINED,
        /** The executable needs external shared libraries: it cannot back an empty observation. */
        DYNAMIC_DEPENDENCIES_PRESENT,
        /** The artifact could not be classified: fail closed. */
        UNKNOWN
    }

    public record Result(Status status, String detail) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
