/*
 * Mayogram download manager - media category classification.
 *
 * Deliberately mirrors DownloadController.AUTODOWNLOAD_TYPE_* rather than
 * inventing a parallel enum, plus one addition (VOICE) that the existing
 * autodownload mask does not separate from AUDIO.
 */

package org.telegram.messenger.download;

import org.telegram.messenger.MessageObject;

public enum DownloadCategory {
    PHOTO("Photos"),
    VIDEO("Videos"),
    DOCUMENT("Documents"),
    AUDIO("Audio"),
    VOICE("Voice");

    /** Sub-folder name under a chat's download directory. */
    public final String folderName;

    DownloadCategory(String folderName) {
        this.folderName = folderName;
    }

    public static DownloadCategory of(MessageObject messageObject) {
        if (messageObject == null) {
            return DOCUMENT;
        }
        if (messageObject.isVoice()) {
            return VOICE;
        }
        if (messageObject.isMusic()) {
            return AUDIO;
        }
        if (messageObject.isVideo() || messageObject.isRoundVideo()) {
            return VIDEO;
        }
        if (messageObject.isPhoto()) {
            return PHOTO;
        }
        return DOCUMENT;
    }
}
