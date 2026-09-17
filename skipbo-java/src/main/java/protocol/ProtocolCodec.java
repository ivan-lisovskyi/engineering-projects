package protocol;

import protocol.common.Feature;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared encoder/decoder for the Skip-Bo wire protocol.
 * This class centralizes parsing and validation of player names, feature flags,
 * card tokens, and position tokens so the client and server always interpret
 * messages the same way.
 * Keeping these rules in one place avoids duplicate parsing logic, prevents
 * subtle protocol drift between client and server, and makes it easier to update
 * the protocol without touching every caller.
 */
public final class ProtocolCodec {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,30}$");
    private static final int MIN_PILE_INDEX = 0;
    private static final int MAX_PILE_INDEX = 3;

    public enum PositionKind {
        HAND,
        STOCK,
        DRAW,
        DISCARD,
        BUILD
    }

    public static final class PositionToken {
        public final PositionKind kind;
        public final int index;
        public final String cardToken;
        public final String normalized;

        private PositionToken(PositionKind kind, int index, String cardToken, String normalized) {
            this.kind = kind;
            this.index = index;
            this.cardToken = cardToken;
            this.normalized = normalized;
        }
    }

    public static final class CardToken {
        public final boolean skipBo;
        public final Integer number;
        public final String normalized;

        private CardToken(boolean skipBo, Integer number, String normalized) {
            this.skipBo = skipBo;
            this.number = number;
            this.normalized = normalized;
        }
    }

    private ProtocolCodec() {
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Parse feature string strictly (C/L/M only, alphabetical, no duplicates).
     * Returns null when invalid.
     */
    public static Feature[] parseFeaturesStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Feature[0];
        }
        String value = raw.toUpperCase(Locale.ROOT);
        Set<Feature> features = new HashSet<>();
        char prev = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            Feature feature = switch (c) {
                case 'C' -> Feature.CHAT;
                case 'L' -> Feature.LOBBY;
                case 'M' -> Feature.MASTER;
                default -> null;
            };
            if (feature == null || c < prev || !features.add(feature)) {
                return null;
            }
            prev = c;
        }
        return features.toArray(new Feature[0]);
    }

    public static CardToken parseCardToken(String token, boolean allowNumberedSkipBo) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.equals("X")) {
            return null;
        }
        if (trimmed.startsWith("SB")) {
            if (trimmed.length() == 2) {
                return new CardToken(true, null, "SB");
            }
            String digits = trimmed.substring(2);
            try {
                int number = Integer.parseInt(digits);
                if (number < 1 || number > 12 || !allowNumberedSkipBo) {
                    return null;
                }
                return new CardToken(true, number, "SB" + number);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            int number = Integer.parseInt(trimmed);
            if (number < 1 || number > 12) {
                return null;
            }
            return new CardToken(false, number, String.valueOf(number));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a protocol position token (S, R, H.<card>, D.<0..3>, B.<0..3>).
     */
    public static PositionToken parsePositionToken(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.equalsIgnoreCase("S")) {
            return new PositionToken(PositionKind.STOCK, -1, null, "S");
        }
        if (trimmed.equalsIgnoreCase("R")) {
            return new PositionToken(PositionKind.DRAW, -1, null, "R");
        }
        if (trimmed.length() >= 2 && trimmed.charAt(1) == '.') {
            char prefix = Character.toUpperCase(trimmed.charAt(0));
            String value = trimmed.substring(2).trim();
            if (prefix == 'H') {
                CardToken card = parseCardToken(value, false);
                if (card == null || (card.skipBo && card.number != null)) {
                    return null;
                }
                return new PositionToken(PositionKind.HAND, -1, card.normalized, "H." + card.normalized);
            }
            if (prefix == 'D' || prefix == 'B') {
                int idx;
                try {
                    idx = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (idx < MIN_PILE_INDEX || idx > MAX_PILE_INDEX) {
                    return null;
                }
                PositionKind kind = prefix == 'D' ? PositionKind.DISCARD : PositionKind.BUILD;
                return new PositionToken(kind, idx, null, prefix + "." + idx);
            }
        }
        return null;
    }
}
