package com.hooshikadev.app;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpreadsheetImporterTest {
    @Test public void importsCsvDetectsConsentAndDeduplicates() throws Exception {
        String csv = "نام,شماره,رضایت,زمینه\nلاله زار,09121234567,بله,برق ساختمان\nتکراری,+989121234567,خیر,عمومی\nصنعت کابل,09351234567,yes,کابل صنعتی\n";
        List<ContactRecord> result = SpreadsheetImporter.parse(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "contacts.csv");
        assertEquals(2, result.size());
        assertEquals("+989121234567", result.get(0).phone);
        assertTrue(result.get(0).consent);
        assertTrue(result.get(0).score >= 40);
    }

    @Test public void importsRealXlsxContainer() throws Exception {
        String shared = "<?xml version=\"1.0\"?><sst><si><t>نام</t></si><si><t>شماره</t></si><si><t>رضایت</t></si><si><t>فروش برق</t></si><si><t>بله</t></si></sst>";
        String sheet = "<?xml version=\"1.0\"?><worksheet><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>3</v></c><c r=\"B2\"><v>09121234567</v></c><c r=\"C2\" t=\"s\"><v>4</v></c></row>"
                + "</sheetData></worksheet>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(shared.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<ContactRecord> result = SpreadsheetImporter.parse(new ByteArrayInputStream(bytes.toByteArray()), "contacts.xlsx");
        assertEquals(1, result.size());
        assertEquals("فروش برق", result.get(0).name);
        assertTrue(result.get(0).consent);
    }
}
