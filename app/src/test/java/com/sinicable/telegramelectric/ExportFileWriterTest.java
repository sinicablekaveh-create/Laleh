package com.sinicable.telegramelectric;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;

public class ExportFileWriterTest {
    @Test
    public void xmlEscapesMarkupAndKeepsAllowedWhitespace() throws Exception {
        assertEquals(
                "A&amp;B &lt;tag&gt; &quot;quoted&quot;&apos;\n\t",
                ExportFileWriter.xml("A&B <tag> \"quoted\"'\n\t")
        );
    }

    @Test
    public void xmlReplacesCharactersForbiddenByXml10() throws Exception {
        assertEquals(
                "before\uFFFDmiddle\uFFFDafter",
                ExportFileWriter.xml("before\u0000middle\u000Bafter")
        );
    }

    @Test
    public void xmlKeepsSupplementaryUnicodeCharacters() throws Exception {
        assertEquals("برق ⚡ \uD83D\uDE80", ExportFileWriter.xml("برق ⚡ \uD83D\uDE80"));
    }

    @Test
    public void generatedWorkbookWithTelegramControlCharactersIsValidXml() throws Exception {
        TelegramClientManager telegram = mock(TelegramClientManager.class);
        TelegramClientManager.GroupInfo group = new TelegramClientManager.GroupInfo(
                1,
                -1001L,
                "گروه\u0000 برق \uD83D\uDE80",
                "https://t.me/electric\u000Bgroup",
                42,
                "عضو",
                true,
                false
        );
        when(telegram.getFoundGroups()).thenReturn(List.of(group));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExportFileWriter.write(
                output,
                telegram,
                ExportFileWriter.EntityType.GROUPS,
                ExportFileWriter.Format.XLSX,
                1,
                1
        );

        byte[] worksheet = zipEntry(output.toByteArray(), "xl/worksheets/sheet1.xml");
        assertNotNull("The XLSX must contain its worksheet", worksheet);
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(worksheet));

        String xml = new String(worksheet, StandardCharsets.UTF_8);
        assertTrue(xml.contains("گروه\uFFFD برق \uD83D\uDE80"));
        assertTrue(xml.contains("https://t.me/electric\uFFFDgroup"));
    }

    private static byte[] zipEntry(byte[] archive, String expectedPath) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (expectedPath.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
            }
        }
        return null;
    }
}
