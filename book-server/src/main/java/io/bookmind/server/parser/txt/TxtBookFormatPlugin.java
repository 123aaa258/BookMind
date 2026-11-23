package io.bookmind.server.parser.txt;

import io.bookmind.spi.BookFormatPlugin;
import io.bookmind.spi.Chapter;
import io.bookmind.spi.ParsedBook;
import io.bookmind.spi.PluginErrorCode;
import io.bookmind.spi.PluginException;
import io.bookmind.spi.PluginMetadata;
import io.bookmind.spi.SpiVersion;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built-in TXT e-book format parser implementing the unified {@link BookFormatPlugin} SPI.
 *
 * <p>Supports UTF-8 (with or without BOM) and GB18030/GBK fallback decoding, extracts title/author
 * headers, and splits Chinese/English chapter headings into structured {@link Chapter}s.
 */
@Component
public class TxtBookFormatPlugin implements BookFormatPlugin {

    public static final String PLUGIN_ID = "builtin-txt";

    private static final PluginMetadata METADATA = new PluginMetadata(
            PLUGIN_ID,
            "Built-in TXT Format Parser",
            "0.1.0",
            SpiVersion.CURRENT_API_VERSION,
            "Built-in UTF-8/GBK TXT e-book parser with smart Chinese/English chapter detection.",
            "BookMind Core"
    );

    private static final Charset GB18030 = Charset.forName("GB18030");

    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "^(第[零一二三四五六七八九十百千万两0-9]+[章节回卷篇部](?:\\s+.*|：.*|:.*)?|"
                    + "(?i)(?:Chapter|Part|Section)\\s+[0-9IVXLCDM]+(?:[.:\\-\\s].*)?)$"
    );
    private static final Pattern BOOK_TITLE_LINE = Pattern.compile("^《([^》]+)》$");
    private static final Pattern AUTHOR_LINE = Pattern.compile("^(?:作者|(?i)Author)\\s*[:：]\\s*(.+)$");
    private static final Pattern CJK_CHAR = Pattern.compile("[\\u4E00-\\u9FFF]");

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("txt");
    }

    @Override
    public ParsedBook parse(Path filePath) throws PluginException {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new PluginException(PluginErrorCode.IO_ERROR, "TXT file does not exist: " + filePath);
        }

        byte[] rawBytes;
        try {
            rawBytes = Files.readAllBytes(filePath);
        } catch (IOException ex) {
            throw new PluginException(
                    PluginErrorCode.IO_ERROR,
                    "Failed to read TXT file " + filePath.getFileName() + ": " + ex.getMessage(),
                    ex
            );
        }

        if (rawBytes.length == 0) {
            throw new PluginException(PluginErrorCode.EMPTY_CONTENT, "TXT file is empty: " + filePath.getFileName());
        }
        if (containsBinaryNullByte(rawBytes)) {
            throw new PluginException(
                    PluginErrorCode.CORRUPTED_FILE,
                    "TXT file appears to contain binary data (NUL byte): " + filePath.getFileName()
            );
        }

        DecodedText decoded = decodeText(rawBytes);
        String normalized = decoded.text().replace("\r\n", "\n").replace("\r", "\n");
        if (normalized.isBlank()) {
            throw new PluginException(
                    PluginErrorCode.EMPTY_CONTENT,
                    "TXT file contains only whitespace: " + filePath.getFileName()
            );
        }

        String defaultTitle = stripExtension(filePath.getFileName().toString());
        ParsedStructure structure = extractStructure(normalized, defaultTitle);
        String language = CJK_CHAR.matcher(normalized).find() ? "zh-CN" : "en";

        return new ParsedBook(
                structure.title(),
                structure.author(),
                language,
                "txt",
                Optional.empty(),
                structure.chapters(),
                Map.of(
                        "encoding", decoded.encoding(),
                        "parserPluginId", PLUGIN_ID,
                        "chapterCount", String.valueOf(structure.chapters().size())
                )
        );
    }

    private static DecodedText decodeText(byte[] rawBytes) throws PluginException {
        int offset = 0;
        if (rawBytes.length >= 3
                && (rawBytes[0] & 0xFF) == 0xEF
                && (rawBytes[1] & 0xFF) == 0xBB
                && (rawBytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        ByteBuffer buffer = ByteBuffer.wrap(rawBytes, offset, rawBytes.length - offset);
        try {
            CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return new DecodedText(utf8.decode(buffer.duplicate()).toString(), "UTF-8");
        } catch (CharacterCodingException ignored) {
            try {
                CharsetDecoder gb = GB18030.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return new DecodedText(gb.decode(buffer.duplicate()).toString(), "GB18030");
            } catch (CharacterCodingException ex) {
                throw new PluginException(
                        PluginErrorCode.CORRUPTED_FILE,
                        "Unable to decode TXT file as UTF-8 or GB18030",
                        ex
                );
            }
        }
    }

    private static ParsedStructure extractStructure(String text, String defaultTitle) {
        String[] lines = text.split("\n", -1);
        String title = defaultTitle;
        String author = "Unknown";

        List<Integer> headingLineIndices = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 80 && CHAPTER_HEADING.matcher(trimmed).matches()) {
                headingLineIndices.add(i);
            }
        }

        int headerLimit = headingLineIndices.isEmpty() ? Math.min(lines.length, 5) : headingLineIndices.get(0);
        int contentStartLine = 0;
        for (int i = 0; i < headerLimit; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher titleMatcher = BOOK_TITLE_LINE.matcher(trimmed);
            if (titleMatcher.matches() && title.equals(defaultTitle)) {
                title = titleMatcher.group(1).trim();
                contentStartLine = i + 1;
                continue;
            }
            Matcher authorMatcher = AUTHOR_LINE.matcher(trimmed);
            if (authorMatcher.matches() && "Unknown".equals(author)) {
                author = authorMatcher.group(1).trim();
                contentStartLine = i + 1;
                continue;
            }
            break;
        }

        List<Chapter> chapters = new ArrayList<>();
        if (headingLineIndices.isEmpty()) {
            String body = joinRange(lines, contentStartLine, lines.length).trim();
            if (body.isEmpty()) {
                body = text.trim();
            }
            chapters.add(new Chapter(0, "全文", "chapter:1", body));
            return new ParsedStructure(title, author, chapters);
        }

        String preface = joinRange(lines, contentStartLine, headingLineIndices.get(0)).trim();
        if (!preface.isEmpty()) {
            chapters.add(new Chapter(chapters.size(), "序言", "chapter:0", preface));
        }

        int chapterNumber = 1;
        for (int idx = 0; idx < headingLineIndices.size(); idx++) {
            int start = headingLineIndices.get(idx);
            int end = (idx + 1 < headingLineIndices.size()) ? headingLineIndices.get(idx + 1) : lines.length;
            String heading = lines[start].trim();
            String body = joinRange(lines, start + 1, end).trim();
            if (body.isEmpty()) {
                body = heading;
            }
            chapters.add(new Chapter(chapters.size(), heading, "chapter:" + chapterNumber, body));
            chapterNumber++;
        }

        return new ParsedStructure(title, author, chapters);
    }

    private static String joinRange(String[] lines, int startInclusive, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static boolean containsBinaryNullByte(byte[] bytes) {
        int limit = Math.min(bytes.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private record DecodedText(String text, String encoding) {
    }

    private record ParsedStructure(String title, String author, List<Chapter> chapters) {
    }
}
