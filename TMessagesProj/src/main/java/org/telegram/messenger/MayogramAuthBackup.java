/*
 * Mayogram: mirrors each logged-in account's session file (tgnet.dat -
 * contains the auth key, DC config, and session state; equivalent to a
 * password - whoever has it can use the account without a phone/OTP) into
 * the visible "Mayogram/Mayogram Auth" folder, alongside the Images/Video/
 * Audio folders already created by ImageLoader.
 *
 * This is a deliberate, explicitly-requested duplication. The internal copy
 * in ApplicationLoader.getFilesDirFixed() is already protected by Android's
 * app sandbox; this external copy is not - it is visible in a file manager,
 * copyable over USB/file sharing, and (on older Android or rooted devices)
 * potentially readable by other apps with storage permission. Anyone who
 * gets a copy of tgnet.dat can impersonate the logged-in account.
 */

package org.telegram.messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MayogramAuthBackup {

    private static final String SESSION_FILE_NAME = "tgnet.dat";
    private static final String AUTH_FOLDER_NAME = "Mayogram Auth";

    private MayogramAuthBackup() {
    }

    public static void backupNow() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File mayogramImagesDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE);
                if (mayogramImagesDir == null || mayogramImagesDir.getParentFile() == null) {
                    return;
                }
                File authDir = new File(mayogramImagesDir.getParentFile(), AUTH_FOLDER_NAME);
                if (!authDir.isDirectory() && !authDir.mkdirs()) {
                    return;
                }
                File internalBase = ApplicationLoader.getFilesDirFixed();
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (!UserConfig.getInstance(a).isClientActivated()) {
                        continue;
                    }
                    File srcDir = a == 0 ? internalBase : new File(internalBase, "account" + a);
                    File src = new File(srcDir, SESSION_FILE_NAME);
                    if (!src.exists()) {
                        continue;
                    }
                    File dst = new File(authDir, "account" + a + "_" + SESSION_FILE_NAME);
                    copyFile(src, dst);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }
}
