package com.lis.astm.model;

/**
 * ASTM E1394 escape sequences for the standard delimiter set {@code |\^&}.
 *
 * <pre>
 *   &amp;F&amp;  field delimiter      '|'
 *   &amp;R&amp;  repeat delimiter     '\'
 *   &amp;S&amp;  component delimiter  '^'
 *   &amp;E&amp;  escape delimiter     '&amp;'
 *   &amp;Xhh..&amp;  hexadecimal data
 * </pre>
 *
 * Sysmex XN uses these inside field values (for example {@code &R&} in PNG image paths and
 * {@code &E&} in research item names such as {@code ~NEUT#&E&}).
 */
public final class AstmEscape {

    private AstmEscape() {}

    /** Decodes escape sequences in a field or component value. Unknown sequences are left untouched. */
    public static String unescape(String value) {
        if (value == null || value.indexOf('&') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '&') {
                int end = value.indexOf('&', i + 1);
                if (end > i + 1) {
                    String code = value.substring(i + 1, end);
                    String replacement = decode(code);
                    if (replacement != null) {
                        out.append(replacement);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Encodes delimiter characters so the value can be placed inside a single field. */
    public static String escape(String value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&': out.append("&E&"); break;
                case '|': out.append("&F&"); break;
                case '\\': out.append("&R&"); break;
                case '^': out.append("&S&"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    private static String decode(String code) {
        switch (code) {
            case "F": return "|";
            case "R": return "\\";
            case "S": return "^";
            case "E": return "&";
            case "H": case "N": return ""; // highlighting on/off: no textual equivalent
            default:
                if (code.length() > 1 && (code.charAt(0) == 'X' || code.charAt(0) == 'x')) {
                    return decodeHex(code.substring(1));
                }
                return null;
        }
    }

    private static String decodeHex(String hex) {
        if (hex.isEmpty() || hex.length() % 2 != 0) return null;
        StringBuilder sb = new StringBuilder();
        try {
            for (int i = 0; i < hex.length(); i += 2) {
                sb.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return sb.toString();
    }
}
