/*
 * Mayogram channel/group membership counter.
 *
 * Reuses Telegram's own applicable limit rather than a hardcoded number:
 * MessagesController.channelsLimitDefault / channelsLimitPremium are synced
 * from the server's help.getAppConfig ("channels_limit_default" /
 * "channels_limit_premium"), the same values Telegram's own client uses
 * (see PremiumPreviewFragment) and the same limit the server enforces when
 * joining a channel/supergroup (CHANNELS_TOO_MUCH). Both broadcast channels
 * and supergroups count against this single limit in Telegram's API, so
 * both are counted here - this is "Channels & Groups", not owned/created
 * channels specifically.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ChannelMembershipCounter {

    private ChannelMembershipCounter() {
    }

    public static int getCurrentCount(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<TLRPC.Dialog> dialogs = controller.getAllDialogs();
        int count = 0;
        for (int i = 0; i < dialogs.size(); i++) {
            long dialogId = dialogs.get(i).id;
            if (dialogId >= 0) {
                continue;
            }
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat != null && ChatObject.isChannel(chat)) {
                count++;
            }
        }
        return count;
    }

    public static int getLimit(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        boolean premium = UserConfig.getInstance(account).isPremium();
        int limit = premium ? controller.channelsLimitPremium : controller.channelsLimitDefault;
        return limit > 0 ? limit : 500;
    }
}
