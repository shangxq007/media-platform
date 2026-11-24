package com.example.platform.config.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Pattern;

/** Derives a display ratio only from explicit SAR and supported stream orientation facts. */
final class SourceDisplayAspect {
    private static final Pattern POSITIVE_PAIR = Pattern.compile("([1-9][0-9]{0,9}):([1-9][0-9]{0,9})");
    private static final BigInteger MAX_COMPONENT = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final long ONE = 65_536;
    private static final long HOMOGENEOUS_ONE = 1_073_741_824;

    private SourceDisplayAspect() {}

    static Optional<String> fromProbe(JsonNode stream) {
        var width = stream.path("width");
        var height = stream.path("height");
        var sar = POSITIVE_PAIR.matcher(stream.path("sample_aspect_ratio").asText(""));
        if (!width.isIntegralNumber() || !width.canConvertToInt() || width.intValue() <= 0
                || !height.isIntegralNumber() || !height.canConvertToInt() || height.intValue() <= 0
                || !sar.matches() || stream.path("disposition").path("attached_pic").asInt(0) != 0) {
            return Optional.empty();
        }
        var numerator = BigInteger.valueOf(width.intValue()).multiply(new BigInteger(sar.group(1)));
        var denominator = BigInteger.valueOf(height.intValue()).multiply(new BigInteger(sar.group(2)));
        var reported = stream.path("display_aspect_ratio");
        if (!reported.isMissingNode() && !"N/A".equals(reported.asText())) {
            var dar = POSITIVE_PAIR.matcher(reported.asText(""));
            if (!dar.matches() || !numerator.multiply(new BigInteger(dar.group(2)))
                    .equals(denominator.multiply(new BigInteger(dar.group(1))))) return Optional.empty();
        }
        var swapsAxes = orientationSwapsAxes(stream);
        if (swapsAxes.isEmpty()) return Optional.empty();
        var divisor = numerator.gcd(denominator);
        numerator = numerator.divide(divisor);
        denominator = denominator.divide(divisor);
        if (numerator.compareTo(MAX_COMPONENT) > 0 || denominator.compareTo(MAX_COMPONENT) > 0) {
            return Optional.empty();
        }
        return Optional.of(swapsAxes.get() ? denominator + ":" + numerator : numerator + ":" + denominator);
    }

    private static Optional<Boolean> orientationSwapsAxes(JsonNode stream) {
        var tag = stream.path("tags").path("rotate");
        Optional<Boolean> tagged = rotationSwapsAxes(tag);
        if (!tag.isMissingNode() && tagged.isEmpty()) return Optional.empty();

        var side = stream.path("side_data_list");
        if (!side.isMissingNode() && !side.isArray()) return Optional.empty();
        Optional<Boolean> matrix = Optional.empty();
        for (var entry : side) {
            if ("Display Matrix".equals(entry.path("side_data_type").asText())) {
                if (matrix.isPresent()) return Optional.empty();
                matrix = matrixSwapsAxes(entry.path("displaymatrix").asText(""));
                if (matrix.isEmpty()) return Optional.empty();
                var rotation = entry.path("rotation");
                if (!rotation.isMissingNode() && !matrix.equals(rotationSwapsAxes(rotation))) return Optional.empty();
            } else if (entry.has("rotation") || entry.has("displaymatrix")) {
                return Optional.empty();
            }
        }
        if (tagged.isPresent() && matrix.isPresent() && !tagged.equals(matrix)) return Optional.empty();
        return matrix.or(() -> tagged).or(() -> Optional.of(false));
    }

    private static Optional<Boolean> rotationSwapsAxes(JsonNode rotation) {
        if (!(rotation.isTextual() || rotation.isIntegralNumber())) return Optional.empty();
        String raw = rotation.asText();
        if (!raw.matches("-?[0-9]{1,4}")) return Optional.empty();
        int degrees = Integer.parseInt(raw);
        return degrees % 90 == 0 ? Optional.of(degrees % 180 != 0) : Optional.empty();
    }

    private static Optional<Boolean> matrixSwapsAxes(String matrix) {
        var lines = matrix.strip().split("\\R");
        if (lines.length != 3) return Optional.empty();
        long[] cells = new long[9];
        for (int row = 0; row < lines.length; row++) {
            var parts = lines[row].strip().split(":\\s*", 2);
            if (parts.length != 2 || !parts[0].equals("0000000" + row)) return Optional.empty();
            var entries = parts[1].split("\\s+");
            if (entries.length != 3) return Optional.empty();
            for (int col = 0; col < entries.length; col++) {
                if (!entries[col].matches("-?[0-9]{1,10}")) return Optional.empty();
                cells[3 * row + col] = Long.parseLong(entries[col]);
            }
        }
        if (cells[2] != 0 || cells[5] != 0 || cells[8] != HOMOGENEOUS_ONE) return Optional.empty();
        if (Math.abs(cells[0]) == ONE && cells[1] == 0 && cells[3] == 0 && Math.abs(cells[4]) == ONE) {
            return Optional.of(false);
        }
        if (cells[0] == 0 && Math.abs(cells[1]) == ONE && Math.abs(cells[3]) == ONE && cells[4] == 0) {
            return Optional.of(true);
        }
        return Optional.empty();
    }
}
