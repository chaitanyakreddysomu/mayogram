/*
 * Mayogram ghost mode.
 *
 * Turns on two of Telegram's own, real, server-enforced privacy settings at
 * once - it does not fake or bypass anything client-side:
 *
 *   - "Last seen & Online" privacy rule (TL_inputPrivacyKeyStatusTimestamp)
 *     set to Nobody, the exact same request PrivacyControlActivity sends
 *     when the user picks "Nobody" by hand.
 *   - The global "hide_read_marks" setting (TL_account.setGlobalPrivacySettings),
 *     the exact same request PrivacyControlActivity sends for its "Read
 *     Receipts" toggle - the server itself then withholds read confirmations
 *     from the people this account talks to.
 *
 * Both changes are genuinely reversible: the previous rules/setting are
 * snapshotted before enabling and restored on disable, so turning ghost mode
 * off puts the account's privacy back exactly where it was.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;

public class GhostModeController {

    public interface ToggleCallback {
        void onResult(boolean success);
    }

    private static final String PREFS = "mayo_ghost_mode";

    private GhostModeController() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(int account) {
        return prefs().getBoolean("enabled_" + account, false);
    }

    public static void setEnabled(int account, boolean enabled, ToggleCallback callback) {
        if (enabled == isEnabled(account)) {
            if (callback != null) {
                callback.onResult(true);
            }
            return;
        }
        if (enabled) {
            enable(account, callback);
        } else {
            disable(account, callback);
        }
    }

    private static void enable(int account, ToggleCallback callback) {
        ContactsController contacts = ContactsController.getInstance(account);
        ArrayList<TLRPC.PrivacyRule> previousRules = contacts.getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
        TLRPC.GlobalPrivacySettings previousGlobal = contacts.getGlobalPrivacySettings();
        boolean previousHideReadMarks = previousGlobal != null && previousGlobal.hide_read_marks;

        prefs().edit()
                .putString("snapshot_rules_" + account, serializeRules(previousRules))
                .putBoolean("snapshot_hide_read_marks_" + account, previousHideReadMarks)
                .apply();

        ArrayList<TLRPC.PrivacyRule> newRules = new ArrayList<>();
        newRules.add(new TLRPC.TL_privacyValueDisallowAll());
        applyAndFinish(account, newRules, true, true, callback);
    }

    private static void disable(int account, ToggleCallback callback) {
        SharedPreferences p = prefs();
        ArrayList<TLRPC.PrivacyRule> restoredRules = deserializeRules(p.getString("snapshot_rules_" + account, null));
        boolean restoredHideReadMarks = p.getBoolean("snapshot_hide_read_marks_" + account, false);
        applyAndFinish(account, restoredRules, restoredHideReadMarks, false, callback);
    }

    private static void applyAndFinish(int account, ArrayList<TLRPC.PrivacyRule> rules, boolean hideReadMarks,
                                        boolean enabling, ToggleCallback callback) {
        final boolean[] anyError = {false};
        final int[] pending = {2};

        Runnable finish = () -> {
            if (--pending[0] == 0) {
                if (!anyError[0]) {
                    prefs().edit().putBoolean("enabled_" + account, enabling).apply();
                }
                if (callback != null) {
                    callback.onResult(!anyError[0]);
                }
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.privacyRulesUpdated);
            }
        };

        TL_account.setPrivacy req = new TL_account.setPrivacy();
        req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        req.rules = toInputRules(account, rules);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TL_account.privacyRules result = (TL_account.privacyRules) response;
                MessagesController.getInstance(account).putUsers(result.users, false);
                MessagesController.getInstance(account).putChats(result.chats, false);
                ContactsController.getInstance(account).setPrivacyRules(result.rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
            } else {
                anyError[0] = true;
                FileLog.e("GhostMode: setPrivacy failed " + error.text);
            }
            finish.run();
        }), ConnectionsManager.RequestFlagFailOnServerErrors);

        TLRPC.GlobalPrivacySettings current = ContactsController.getInstance(account).getGlobalPrivacySettings();
        TL_account.setGlobalPrivacySettings req2 = new TL_account.setGlobalPrivacySettings();
        req2.settings = new TLRPC.TL_globalPrivacySettings();
        if (current != null) {
            req2.settings.archive_and_mute_new_noncontact_peers = current.archive_and_mute_new_noncontact_peers;
            req2.settings.keep_archived_folders = current.keep_archived_folders;
            req2.settings.keep_archived_unmuted = current.keep_archived_unmuted;
            req2.settings.new_noncontact_peers_require_premium = current.new_noncontact_peers_require_premium;
            req2.settings.noncontact_peers_paid_stars = current.noncontact_peers_paid_stars;
            req2.settings.display_gifts_button = current.display_gifts_button;
        }
        req2.settings.hide_read_marks = hideReadMarks;
        ConnectionsManager.getInstance(account).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.GlobalPrivacySettings settings = ContactsController.getInstance(account).getGlobalPrivacySettings();
                if (settings != null) {
                    settings.hide_read_marks = ((TLRPC.TL_globalPrivacySettings) response).hide_read_marks;
                }
            } else {
                anyError[0] = true;
                FileLog.e("GhostMode: setGlobalPrivacySettings failed " + error.text);
            }
            finish.run();
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static ArrayList<TLRPC.InputPrivacyRule> toInputRules(int account, ArrayList<TLRPC.PrivacyRule> rules) {
        ArrayList<TLRPC.InputPrivacyRule> result = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            result.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
            return result;
        }
        for (TLRPC.PrivacyRule rule : rules) {
            TLRPC.InputPrivacyRule converted = convert(account, rule);
            if (converted != null) {
                result.add(converted);
            }
        }
        if (result.isEmpty()) {
            result.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        }
        return result;
    }

    private static TLRPC.InputPrivacyRule convert(int account, TLRPC.PrivacyRule rule) {
        if (rule instanceof TLRPC.TL_privacyValueAllowAll) {
            return new TLRPC.TL_inputPrivacyValueAllowAll();
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowAll) {
            return new TLRPC.TL_inputPrivacyValueDisallowAll();
        } else if (rule instanceof TLRPC.TL_privacyValueAllowContacts) {
            return new TLRPC.TL_inputPrivacyValueAllowContacts();
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowContacts) {
            return new TLRPC.TL_inputPrivacyValueDisallowContacts();
        } else if (rule instanceof TLRPC.TL_privacyValueAllowPremium) {
            return new TLRPC.TL_inputPrivacyValueAllowPremium();
        } else if (rule instanceof TLRPC.TL_privacyValueAllowCloseFriends) {
            return new TLRPC.TL_inputPrivacyValueAllowCloseFriends();
        } else if (rule instanceof TLRPC.TL_privacyValueAllowBots) {
            return new TLRPC.TL_inputPrivacyValueAllowBots();
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowBots) {
            return new TLRPC.TL_inputPrivacyValueDisallowBots();
        } else if (rule instanceof TLRPC.TL_privacyValueAllowUsers) {
            TLRPC.TL_inputPrivacyValueAllowUsers converted = new TLRPC.TL_inputPrivacyValueAllowUsers();
            for (long id : ((TLRPC.TL_privacyValueAllowUsers) rule).users) {
                TLRPC.InputUser inputUser = MessagesController.getInstance(account).getInputUser(id);
                if (inputUser != null) {
                    converted.users.add(inputUser);
                }
            }
            return converted;
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowUsers) {
            TLRPC.TL_inputPrivacyValueDisallowUsers converted = new TLRPC.TL_inputPrivacyValueDisallowUsers();
            for (long id : ((TLRPC.TL_privacyValueDisallowUsers) rule).users) {
                TLRPC.InputUser inputUser = MessagesController.getInstance(account).getInputUser(id);
                if (inputUser != null) {
                    converted.users.add(inputUser);
                }
            }
            return converted;
        } else if (rule instanceof TLRPC.TL_privacyValueAllowChatParticipants) {
            TLRPC.TL_inputPrivacyValueAllowChatParticipants converted = new TLRPC.TL_inputPrivacyValueAllowChatParticipants();
            converted.chats = ((TLRPC.TL_privacyValueAllowChatParticipants) rule).chats;
            return converted;
        } else if (rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants) {
            TLRPC.TL_inputPrivacyValueDisallowChatParticipants converted = new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();
            converted.chats = ((TLRPC.TL_privacyValueDisallowChatParticipants) rule).chats;
            return converted;
        }
        return null;
    }

    private static String serializeRules(ArrayList<TLRPC.PrivacyRule> rules) {
        SerializedData data = new SerializedData();
        int count = rules != null ? rules.size() : 0;
        data.writeInt32(count);
        if (rules != null) {
            for (TLRPC.PrivacyRule rule : rules) {
                rule.serializeToStream(data);
            }
        }
        String result = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        data.cleanup();
        return result;
    }

    private static ArrayList<TLRPC.PrivacyRule> deserializeRules(String base64) {
        ArrayList<TLRPC.PrivacyRule> rules = new ArrayList<>();
        if (TextUtils.isEmpty(base64)) {
            return rules;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            SerializedData data = new SerializedData(bytes);
            int count = data.readInt32(false);
            for (int i = 0; i < count; i++) {
                TLRPC.PrivacyRule rule = TLRPC.PrivacyRule.TLdeserialize(data, data.readInt32(false), false);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            data.cleanup();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return rules;
    }
}
