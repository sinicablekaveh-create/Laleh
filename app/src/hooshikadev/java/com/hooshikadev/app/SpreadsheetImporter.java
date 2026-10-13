package com.hooshikadev.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public final class SpreadsheetImporter {
    private SpreadsheetImporter() {}

    public static List<ContactRecord> parse(InputStream input, String fileName) throws Exception {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        List<List<String>> rows = lower.endsWith(".xlsx") ? parseXlsx(input) : parseDelimited(input);
        return toContacts(rows);
    }

    static List<List<String>> parseDelimited(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        List<List<String>> rows = new ArrayList<>();
        String line;
        char delimiter = ',';
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (first) {
                if (line.indexOf('\t') >= 0) delimiter = '\t';
                else if (line.indexOf(';') >= 0 && line.indexOf(',') < 0) delimiter = ';';
                line = line.replace("\uFEFF", "");
                first = false;
            }
            rows.add(split(line, delimiter));
        }
        return rows;
    }

    private static List<String> split(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else current.append(c);
        }
        cells.add(current.toString().trim());
        return cells;
    }

    static List<List<String>> parseXlsx(InputStream input) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(input);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (!entry.isDirectory() && (entry.getName().equals("xl/sharedStrings.xml")
                    || entry.getName().startsWith("xl/worksheets/sheet"))) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) bytes.write(buffer, 0, read);
                entries.put(entry.getName(), bytes.toByteArray());
            }
        }
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) throw new IOException("فایل اکسل sheet1 ندارد.");
        List<String> shared = parseShared(entries.get("xl/sharedStrings.xml"));
        Document document = xml(sheet);
        NodeList rowNodes = document.getElementsByTagName("row");
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            NodeList cellNodes = row.getElementsByTagName("c");
            Map<Integer, String> values = new HashMap<>();
            int max = -1;
            for (int c = 0; c < cellNodes.getLength(); c++) {
                Element cell = (Element) cellNodes.item(c);
                int column = columnIndex(cell.getAttribute("r"));
                String type = cell.getAttribute("t");
                String value = firstText(cell, "v");
                if ("inlineStr".equals(type)) value = firstText(cell, "t");
                if ("s".equals(type) && !value.isEmpty()) {
                    int index = Integer.parseInt(value);
                    value = index >= 0 && index < shared.size() ? shared.get(index) : "";
                }
                values.put(column, value);
                max = Math.max(max, column);
            }
            List<String> result = new ArrayList<>();
            for (int c = 0; c <= max; c++) result.add(values.getOrDefault(c, ""));
            rows.add(result);
        }
        return rows;
    }

    private static List<String> parseShared(byte[] xml) throws Exception {
        List<String> result = new ArrayList<>();
        if (xml == null) return result;
        NodeList items = xml(xml).getElementsByTagName("si");
        for (int i = 0; i < items.getLength(); i++) result.add(items.item(i).getTextContent());
        return result;
    }

    private static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static String firstText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return "";
        Node node = nodes.item(0);
        return node == null ? "" : node.getTextContent().trim();
    }

    private static int columnIndex(String reference) {
        int value = 0;
        for (int i = 0; i < reference.length(); i++) {
            char c = reference.charAt(i);
            if (c < 'A' || c > 'Z') break;
            value = value * 26 + c - 'A' + 1;
        }
        return Math.max(0, value - 1);
    }

    static List<ContactRecord> toContacts(List<List<String>> rows) {
        LinkedHashMap<String, ContactRecord> unique = new LinkedHashMap<>();
        if (rows.isEmpty()) return new ArrayList<>();
        List<String> header = rows.get(0);
        int phoneColumn = findColumn(header, "phone", "mobile", "tel", "شماره", "موبایل", "تلفن");
        int nameColumn = findColumn(header, "name", "نام", "company", "شرکت");
        int consentColumn = findColumn(header, "consent", "optin", "اجازه", "رضایت");
        boolean hasHeader = phoneColumn >= 0;
        if (!hasHeader) phoneColumn = guessPhoneColumn(rows);
        if (nameColumn < 0) nameColumn = phoneColumn == 0 ? 1 : 0;

        for (int r = hasHeader ? 1 : 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String phone = PhoneNormalizer.normalize(cell(row, phoneColumn));
            if (phone.isEmpty()) continue;
            String name = cell(row, nameColumn);
            String consentText = cell(row, consentColumn).toLowerCase(Locale.ROOT);
            boolean consent = consentText.equals("yes") || consentText.equals("true")
                    || consentText.equals("1") || consentText.equals("بله") || consentText.equals("دارم");
            int score = RelevanceEngine.score(name + " " + join(row));
            ContactRecord next = new ContactRecord(name, phone, consent, score);
            ContactRecord old = unique.get(phone);
            if (old == null || next.score > old.score) unique.put(phone, next);
        }
        return new ArrayList<>(unique.values());
    }

    private static int findColumn(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String cell = header.get(i).trim().toLowerCase(Locale.ROOT).replace("_", "");
            for (String name : names) if (cell.contains(name)) return i;
        }
        return -1;
    }

    private static int guessPhoneColumn(List<List<String>> rows) {
        int maxColumns = 0;
        for (List<String> row : rows) maxColumns = Math.max(maxColumns, row.size());
        int best = 0, bestCount = -1;
        for (int c = 0; c < maxColumns; c++) {
            int count = 0;
            for (List<String> row : rows) if (!PhoneNormalizer.normalize(cell(row, c)).isEmpty()) count++;
            if (count > bestCount) { best = c; bestCount = count; }
        }
        return best;
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index).trim() : "";
    }

    private static String join(List<String> row) {
        StringBuilder builder = new StringBuilder();
        for (String value : row) builder.append(' ').append(value);
        return builder.toString();
    }
}
