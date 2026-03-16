package mofo.com.pestscout.analytics.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal PDF builder for plain text exports.
 * This intentionally supports only the subset needed for raw-data exports.
 */
final class SimplePdfDocumentBuilder {

    private static final int MAX_CHARS_PER_LINE = 92;
    private static final int MAX_LINES_PER_PAGE = 44;

    byte[] build(String title, List<String> sourceLines) {
        List<String> normalizedLines = new ArrayList<>();
        normalizedLines.add(title);
        normalizedLines.add("");
        sourceLines.forEach(line -> normalizedLines.addAll(wrap(line)));

        List<List<String>> pages = paginate(normalizedLines);
        StringBuilder document = new StringBuilder("%PDF-1.4\n");
        List<Integer> objectOffsets = new ArrayList<>();

        int fontObjectNumber = 3 + (pages.size() * 2);

        objectOffsets.add(document.length());
        document.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        objectOffsets.add(document.length());
        document.append("2 0 obj\n<< /Type /Pages /Count ")
                .append(pages.size())
                .append(" /Kids [");
        for (int i = 0; i < pages.size(); i++) {
            document.append(pageObjectNumber(i)).append(" 0 R ");
        }
        document.append("] >>\nendobj\n");

        for (int i = 0; i < pages.size(); i++) {
            int pageObjectNumber = pageObjectNumber(i);
            int contentObjectNumber = contentObjectNumber(i);

            objectOffsets.add(document.length());
            document.append(pageObjectNumber)
                    .append(" 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] ")
                    .append("/Resources << /Font << /F1 ")
                    .append(fontObjectNumber)
                    .append(" 0 R >> >> /Contents ")
                    .append(contentObjectNumber)
                    .append(" 0 R >>\nendobj\n");

            String contentStream = buildContentStream(pages.get(i));
            byte[] contentBytes = contentStream.getBytes(StandardCharsets.US_ASCII);

            objectOffsets.add(document.length());
            document.append(contentObjectNumber)
                    .append(" 0 obj\n<< /Length ")
                    .append(contentBytes.length)
                    .append(" >>\nstream\n")
                    .append(contentStream)
                    .append("endstream\nendobj\n");
        }

        objectOffsets.add(document.length());
        document.append(fontObjectNumber)
                .append(" 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");

        int xrefOffset = document.length();
        document.append("xref\n0 ").append(objectOffsets.size() + 1).append('\n');
        document.append("0000000000 65535 f \n");
        for (Integer offset : objectOffsets) {
            document.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }

        document.append("trailer\n<< /Size ")
                .append(objectOffsets.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n")
                .append(xrefOffset)
                .append("\n%%EOF");

        return document.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private List<List<String>> paginate(List<String> lines) {
        List<List<String>> pages = new ArrayList<>();
        List<String> currentPage = new ArrayList<>();

        for (String line : lines) {
            if (currentPage.size() == MAX_LINES_PER_PAGE) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            currentPage.add(line);
        }

        if (currentPage.isEmpty()) {
            currentPage.add("");
        }
        pages.add(currentPage);
        return pages;
    }

    private List<String> wrap(String line) {
        if (line == null || line.isBlank()) {
            return List.of("");
        }

        List<String> wrapped = new ArrayList<>();
        String remaining = line.trim();
        while (remaining.length() > MAX_CHARS_PER_LINE) {
            int breakIndex = remaining.lastIndexOf(' ', MAX_CHARS_PER_LINE);
            if (breakIndex < 0) {
                breakIndex = MAX_CHARS_PER_LINE;
            }
            wrapped.add(remaining.substring(0, breakIndex).trim());
            remaining = remaining.substring(breakIndex).trim();
        }
        if (!remaining.isEmpty()) {
            wrapped.add(remaining);
        }
        return wrapped;
    }

    private String buildContentStream(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 10 Tf\n14 TL\n50 760 Td\n");
        for (String line : lines) {
            stream.append('(').append(escape(line)).append(") Tj\nT*\n");
        }
        stream.append("ET\n");
        return stream.toString();
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private int pageObjectNumber(int pageIndex) {
        return 3 + (pageIndex * 2);
    }

    private int contentObjectNumber(int pageIndex) {
        return 4 + (pageIndex * 2);
    }
}
