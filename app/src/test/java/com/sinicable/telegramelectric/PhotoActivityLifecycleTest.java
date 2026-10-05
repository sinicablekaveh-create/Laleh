package com.sinicable.telegramelectric;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PhotoActivityLifecycleTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void destroyedActivityCannotReplaceOrDeleteTheActivePhoto() throws Exception {
        MainActivity activity = mock(MainActivity.class, CALLS_REAL_METHODS);
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        Uri uri = mock(Uri.class);
        CentralCorePanel panel = mock(CentralCorePanel.class);
        CentralCore core = mock(CentralCore.class);
        Toast toast = mock(Toast.class);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        AtomicBoolean destroyed = new AtomicBoolean(false);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch resumeCopy = new CountDownLatch(1);
        File previous = new File(folder.getRoot(), "auto_message_photo.jpg");
        Files.write(previous.toPath(), new byte[] {10, 20, 30});

        doReturn(context).when(activity).getApplicationContext();
        doAnswer(call -> destroyed.get()).when(activity).isDestroyed();
        doReturn(false).when(activity).isFinishing();
        doAnswer(call -> { ui.add(call.getArgument(0)); return null; })
                .when(activity).runOnUiThread(any(Runnable.class));
        when(context.getContentResolver()).thenReturn(resolver);
        when(context.getFilesDir()).thenReturn(folder.getRoot());
        when(panel.getCore()).thenReturn(core);
        when(core.getPhotoPath()).thenReturn(previous.getAbsolutePath());
        when(resolver.openInputStream(uri)).thenReturn(new InputStream() {
            private boolean first = true;
            @Override public int read() { return -1; }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (!first) return -1;
                first = false;
                reading.countDown();
                try {
                    if (!resumeCopy.await(5, TimeUnit.SECONDS)) throw new IOException("copy timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException(error);
                }
                bytes[offset] = 99;
                return 1;
            }
        });
        setField(activity, "corePanel", panel);
        setField(activity, "fileExecutor", worker);

        try (MockedStatic<Toast> ignored = mockStatic(Toast.class, call -> toast)) {
            Method select = MainActivity.class.getDeclaredMethod("handleSelectedMessagePhoto", Uri.class);
            select.setAccessible(true);
            select.invoke(activity, uri);
            assertTrue("worker entered photo copy", reading.await(5, TimeUnit.SECONDS));
            destroyed.set(true);
            resumeCopy.countDown();
            Runnable completion = ui.poll(5, TimeUnit.SECONDS);
            assertNotNull("photo completion posted to UI", completion);
            completion.run();
            worker.shutdown();
            assertTrue("photo job completed", worker.awaitTermination(5, TimeUnit.SECONDS));
            assertArrayEquals(new byte[] {10, 20, 30}, Files.readAllBytes(previous.toPath()));
            assertEquals(1, folder.getRoot().list().length);
            verify(core, never()).setPhotoPath(anyString());
        } finally {
            resumeCopy.countDown();
            worker.shutdownNow();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
