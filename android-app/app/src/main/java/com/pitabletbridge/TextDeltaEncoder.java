package com.pitabletbridge;

import java.util.ArrayList;
import java.util.List;

public final class TextDeltaEncoder {
    private TextDeltaEncoder() {
    }

    public static List<KeyCommand> computeCommands(String previous, String current) {
        String safePrevious = previous == null ? "" : previous;
        String safeCurrent = current == null ? "" : current;
        int prefix = 0;
        int maxPrefix = Math.min(safePrevious.length(), safeCurrent.length());
        while (prefix < maxPrefix && safePrevious.charAt(prefix) == safeCurrent.charAt(prefix)) {
            prefix++;
        }

        int previousSuffixIndex = safePrevious.length() - 1;
        int currentSuffixIndex = safeCurrent.length() - 1;
        int suffix = 0;
        while (previousSuffixIndex >= prefix
                && currentSuffixIndex >= prefix
                && safePrevious.charAt(previousSuffixIndex) == safeCurrent.charAt(currentSuffixIndex)) {
            suffix++;
            previousSuffixIndex--;
            currentSuffixIndex--;
        }

        int deleted = safePrevious.length() - prefix - suffix;
        String inserted = safeCurrent.substring(prefix, safeCurrent.length() - suffix);

        List<KeyCommand> commands = new ArrayList<KeyCommand>();
        if (deleted > 0) {
            commands.add(KeyCommand.backspace(deleted));
        }
        if (inserted.length() > 0) {
            commands.add(KeyCommand.text(inserted));
        }
        return commands;
    }
}

