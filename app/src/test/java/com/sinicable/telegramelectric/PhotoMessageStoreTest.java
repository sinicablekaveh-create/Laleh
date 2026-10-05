package com.sinicable.telegramelectric;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PhotoMessageStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Context context;
    private ContentResolver resolver;
    private Uri uri;
    private File oldPhoto;

    @Before
    public void setUp() throws Exception {
        context = mock(Context.class);
        resolver = mock(ContentResolver.class);
        uri = mock(Uri.class);
        when(context.getContentResolver()).thenReturn(resolver);
        when(context.getFilesDir()).thenReturn(folder.getRoot());
        oldPhoto = new File(folder.getRoot(), "auto_message_photo.jpg");
        Files.write(oldPhoto.toPath(), new byte[] {10, 20, 30});
    }

    @Test
    public void unreadableSelectionKeepsPreviousPhoto() throws Exception {
        when(resolver.openInputStream(uri)).thenThrow(new FileNotFoundException("cannot open selection"));
        assertThrows(FileNotFoundException.class, () -> PhotoMessageStore.copyIntoApp(context, uri));
        assertArrayEquals(new byte[] {10, 20, 30}, Files.readAllBytes(oldPhoto.toPath()));
    }

    @Test
    public void interruptedCopyRemovesPartialFileAndKeepsPreviousPhoto() throws Exception {
        when(resolver.openInputStream(uri)).thenReturn(new InputStream() {
            private boolean first = true;
            @Override public int read() throws IOException { throw new IOException("read interrupted"); }
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (!first) throw new IOException("read interrupted");
                first = false;
                buffer[offset] = 99;
                return 1;
            }
        });
        assertThrows(IOException.class, () -> PhotoMessageStore.copyIntoApp(context, uri));
        assertArrayEquals(new byte[] {10, 20, 30}, Files.readAllBytes(oldPhoto.toPath()));
        assertEquals(Arrays.asList("auto_message_photo.jpg"), Arrays.asList(folder.getRoot().list()));
    }

    @Test
    public void completeCopyIsSeparateUntilTheCallerSelectsIt() throws Exception {
        when(resolver.openInputStream(uri)).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        String path = PhotoMessageStore.copyIntoApp(context, uri);
        assertNotEquals(oldPhoto.getAbsolutePath(), path);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(new File(path).toPath()));
        assertArrayEquals(new byte[] {10, 20, 30}, Files.readAllBytes(oldPhoto.toPath()));
        PhotoMessageStore.clear(context, path);
        assertFalse(oldPhoto.exists());
        assertTrue(new File(path).isFile());
    }

    @Test
    public void emptySelectionKeepsPreviousPhoto() throws Exception {
        when(resolver.openInputStream(uri)).thenReturn(new ByteArrayInputStream(new byte[0]));
        assertThrows(IllegalStateException.class, () -> PhotoMessageStore.copyIntoApp(context, uri));
        assertArrayEquals(new byte[] {10, 20, 30}, Files.readAllBytes(oldPhoto.toPath()));
        assertEquals(1, folder.getRoot().list().length);
    }
}
