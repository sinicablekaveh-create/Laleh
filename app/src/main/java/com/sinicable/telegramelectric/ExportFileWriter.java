package com.sinicable.telegramelectric;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ExportFileWriter {
    enum EntityType {
        CONTACTS,
        GROUPS
    }

    enum Format {
        TXT,
        XLSX
    }

    static int countInRange(
            TelegramClientManager telegram,
            EntityType entityType,
            int start,
            int end
    ) {
        if (entityType == EntityType.CONTACTS) {
            int count = 0;
            for (TelegramClientManager.ContactInfo item : telegram.getTelegramContacts()) {
                if (item.number >= start && item.number <= end) count++;
            }
            return count;
        }

        int count = 0;
        for (TelegramClientManager.GroupInfo item : telegram.getFoundGroups()) {
            if (item.number >= start && item.number <= end) count++;
        }
        return count;
    }

    static void write(
            OutputStream output,
            TelegramClientManager telegram,
            EntityType entityType,
            Format format,
            int start,
            int end
    ) throws IOException {
        if (format == Format.XLSX) {
            writeXlsx(output, telegram, entityType, start, end);
        } else {
            writeTxt(output, telegram, entityType, start, end);
        }
    }

    private static void writeTxt(
            OutputStream output,
            TelegramClientManager telegram,
            EntityType entityType,
            int start,
            int end
    ) throws IOException {
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8)
        );

        if (entityType == EntityType.CONTACTS) {
            writer.write("مخاطبین Telegram Electric — بازه " + start + " تا " + end);
            writer.newLine();
            writer.newLine();

            for (TelegramClientManager.ContactInfo item : telegram.getTelegramContacts()) {
                if (item.number < start || item.number > end) continue;
                writer.write("شماره رکورد: " + item.number);
                writer.newLine();
                writer.write("اسم مخاطب: " + item.name);
                writer.newLine();
                writer.write("شماره تلفن: " + item.phone);
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
            }
        } else {
            writer.write("گروه‌های Telegram Electric — بازه " + start + " تا " + end);
            writer.newLine();
            writer.newLine();

            for (TelegramClientManager.GroupInfo item : telegram.getFoundGroups()) {
                if (item.number < start || item.number > end) continue;
                writer.write("شماره رکورد: " + item.number);
                writer.newLine();
                writer.write("اسم گروه: " + item.title);
                writer.newLine();
                writer.write("لینک: " + item.link);
                writer.newLine();
                writer.write("تعداد اعضا: " + item.memberCount);
                writer.newLine();
                writer.write("وضعیت: " + item.status);
                writer.newLine();
                writer.write("----------------------------------------");
                writer.newLine();
            }
        }

        writer.flush();
    }

    private static void writeXlsx(
            OutputStream output,
            TelegramClientManager telegram,
            EntityType entityType,
            int start,
            int end
    ) throws IOException {
        List<List<String>> rows = new ArrayList<>();

        if (entityType == EntityType.CONTACTS) {
            rows.add(row("شماره", "اسم مخاطب", "شماره تلفن"));
            for (TelegramClientManager.ContactInfo item : telegram.getTelegramContacts()) {
                if (item.number < start || item.number > end) continue;
                rows.add(row(
                        String.valueOf(item.number),
                        item.name,
                        item.phone
                ));
            }
        } else {
            rows.add(row("شماره", "اسم گروه", "لینک", "تعداد اعضا", "وضعیت"));
            for (TelegramClientManager.GroupInfo item : telegram.getFoundGroups()) {
                if (item.number < start || item.number > end) continue;
                rows.add(row(
                        String.valueOf(item.number),
                        item.title,
                        item.link,
                        String.valueOf(item.memberCount),
                        item.status
                ));
            }
        }

        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);

        put(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "</Types>");

        put(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");

        put(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"Export\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                        + "</workbook>");

        put(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "</Relationships>");

        StringBuilder sheet = new StringBuilder(8192);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sheet.append("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"/></sheetViews>");
        sheet.append("<sheetData>");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int excelRow = rowIndex + 1;
            sheet.append("<row r=\"").append(excelRow).append("\">");

            List<String> values = rows.get(rowIndex);
            for (int col = 0; col < values.size(); col++) {
                String ref = columnName(col + 1) + excelRow;
                sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(xml(values.get(col)))
                        .append("</t></is></c>");
            }

            sheet.append("</row>");
        }

        sheet.append("</sheetData></worksheet>");
        put(zip, "xl/worksheets/sheet1.xml", sheet.toString());

        zip.finish();
        zip.flush();
    }

    private static List<String> row(String... values) {
        List<String> row = new ArrayList<>(values.length);
        for (String value : values) {
            row.add(value == null ? "" : value);
        }
        return row;
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String columnName(int oneBasedColumn) {
        StringBuilder result = new StringBuilder();
        int value = oneBasedColumn;
        while (value > 0) {
            value--;
            result.insert(0, (char) ('A' + (value % 26)));
            value /= 26;
        }
        return result.toString();
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private ExportFileWriter() {
    }
}
