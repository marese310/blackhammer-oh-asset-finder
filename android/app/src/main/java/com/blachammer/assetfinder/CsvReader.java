package com.blachammer.assetfinder;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

final class CsvReader {
    private final PushbackReader reader;
    private boolean firstCharacter = true;

    CsvReader(Reader reader) {
        this.reader = new PushbackReader(reader, 1);
    }

    List<String> readRow() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean sawCharacter = false;

        while (true) {
            int next = reader.read();
            if (next == -1) {
                if (!sawCharacter && fields.isEmpty() && field.length() == 0) {
                    return null;
                }
                fields.add(field.toString());
                return fields;
            }

            char character = (char) next;
            if (firstCharacter) {
                firstCharacter = false;
                if (character == '\uFEFF') {
                    continue;
                }
            }
            sawCharacter = true;

            if (quoted) {
                if (character == '"') {
                    int following = reader.read();
                    if (following == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (following != -1) {
                            reader.unread(following);
                        }
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (character == '"' && field.length() == 0) {
                quoted = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                if (character == '\r') {
                    int following = reader.read();
                    if (following != '\n' && following != -1) {
                        reader.unread(following);
                    }
                }
                fields.add(field.toString());
                return fields;
            } else {
                field.append(character);
            }
        }
    }
}
