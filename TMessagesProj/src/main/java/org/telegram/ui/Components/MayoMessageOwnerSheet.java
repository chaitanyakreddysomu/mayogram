/*
 * Mayogram: "Show message owner" - a small info sheet surfacing data that's
 * already genuinely available on the message, honestly labeled:
 *   - who originally posted it (message.fwd_from, when the message was
 *     forwarded - this is the ORIGINAL author/channel, not "everyone who
 *     ever forwarded it", which Telegram's API does not expose to anyone)
 *   - who sent this specific copy into the chat you're viewing it in
 *   - for channel posts the current account administers with Telegram's
 *     own stats enabled (chatInfo.can_view_stats), a shortcut into the
 *     existing stock "View Statistics" screen, which can show which
 *     PUBLIC channels forwarded the post - never individual users, since
 *     that data is never exposed by the API regardless of admin status.
 *
 * This deliberately does NOT claim to show "everyone who forwarded this
 * message" - no such list exists.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.MessageStatisticActivity;
import org.telegram.ui.ProfileActivity;

public class MayoMessageOwnerSheet {

    public static void show(Context context, BaseFragment fragment, MessageObject messageObject, boolean canViewPublicForwards, Theme.ResourcesProvider resourcesProvider) {
        if (context == null || fragment == null || messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView title = new TextView(context);
        title.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setText(getString(R.string.MayoMessageOwnerTitle));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 4, 4, 4, 12));

        // --- Originally posted by -------------------------------------------------
        TLRPC.MessageFwdHeader fwdFrom = messageObject.messageOwner.fwd_from;
        if (fwdFrom != null) {
            String originName = messageObject.getForwardedName();
            TLObjectPeer originPeer = resolvePeer(messageObject);
            addRow(context, layout, R.drawable.msg_channel, getString(R.string.MayoOriginallyPostedBy),
                    originName != null ? originName : getString(R.string.MayoUnknownSender),
                    originPeer != null ? () -> openProfile(fragment, originPeer) : null, resourcesProvider);
        } else {
            addRow(context, layout, R.drawable.msg_channel, getString(R.string.MayoOriginallyPostedBy),
                    getString(R.string.MayoOriginalMessageNotForwarded), null, resourcesProvider);
        }

        // --- Shared with you by (only meaningful for an actual forward) -----------
        if (fwdFrom != null && messageObject.isForwarded()) {
            TLObjectPeer senderPeer = resolveFromPeer(messageObject);
            if (senderPeer != null) {
                addRow(context, layout, R.drawable.msg_forward, getString(R.string.MayoSharedWithYouBy),
                        senderPeer.name, () -> openProfile(fragment, senderPeer), resourcesProvider);
            }
        }

        // --- Public forwards (admin + stats-enabled only, reuses the stock screen) -
        if (canViewPublicForwards) {
            addRow(context, layout, R.drawable.msg_stats, getString(R.string.MayoViewPublicForwards),
                    null, () -> {
                        sheet.dismiss();
                        fragment.presentFragment(new MessageStatisticActivity(messageObject));
                    }, resourcesProvider);
        }

        sheet.setCustomView(layout);
        sheet.show();
    }

    private static class TLObjectPeer {
        final TLRPC.User user;
        final TLRPC.Chat chat;
        final String name;
        TLObjectPeer(TLRPC.User user, TLRPC.Chat chat, String name) {
            this.user = user;
            this.chat = chat;
            this.name = name;
        }
    }

    private static TLObjectPeer resolvePeer(MessageObject messageObject) {
        TLRPC.MessageFwdHeader fwdFrom = messageObject.messageOwner.fwd_from;
        if (fwdFrom == null || fwdFrom.from_id == null) {
            return null;
        }
        MessagesController controller = MessagesController.getInstance(messageObject.currentAccount);
        if (fwdFrom.from_id instanceof TLRPC.TL_peerUser) {
            TLRPC.User user = controller.getUser(fwdFrom.from_id.user_id);
            if (user != null) {
                return new TLObjectPeer(user, null, UserObject.getUserName(user));
            }
        } else if (fwdFrom.from_id instanceof TLRPC.TL_peerChannel) {
            TLRPC.Chat chat = controller.getChat(fwdFrom.from_id.channel_id);
            if (chat != null) {
                return new TLObjectPeer(null, chat, chat.title);
            }
        } else if (fwdFrom.from_id instanceof TLRPC.TL_peerChat) {
            TLRPC.Chat chat = controller.getChat(fwdFrom.from_id.chat_id);
            if (chat != null) {
                return new TLObjectPeer(null, chat, chat.title);
            }
        }
        return null;
    }

    private static TLObjectPeer resolveFromPeer(MessageObject messageObject) {
        TLRPC.Peer fromId = messageObject.messageOwner.from_id;
        if (fromId == null) {
            return null;
        }
        MessagesController controller = MessagesController.getInstance(messageObject.currentAccount);
        if (fromId instanceof TLRPC.TL_peerUser) {
            TLRPC.User user = controller.getUser(fromId.user_id);
            if (user != null) {
                return new TLObjectPeer(user, null, UserObject.getUserName(user));
            }
        } else if (fromId instanceof TLRPC.TL_peerChannel) {
            TLRPC.Chat chat = controller.getChat(fromId.channel_id);
            if (chat != null) {
                return new TLObjectPeer(null, chat, chat.title);
            }
        } else if (fromId instanceof TLRPC.TL_peerChat) {
            TLRPC.Chat chat = controller.getChat(fromId.chat_id);
            if (chat != null) {
                return new TLObjectPeer(null, chat, chat.title);
            }
        }
        return null;
    }

    private static void openProfile(BaseFragment fragment, TLObjectPeer peer) {
        Bundle args = new Bundle();
        if (peer.user != null) {
            args.putLong("user_id", peer.user.id);
        } else if (peer.chat != null) {
            args.putLong("chat_id", peer.chat.id);
        } else {
            return;
        }
        fragment.presentFragment(new ProfileActivity(args));
    }

    private static void addRow(Context context, LinearLayout layout, int iconRes, String label, String value, Runnable onClick, Theme.ResourcesProvider resourcesProvider) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        if (onClick != null) {
            row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
            row.setOnClickListener(v -> onClick.run());
        }

        ImageView icon = new ImageView(context);
        Drawable drawable = ContextCompat.getDrawable(context, iconRes);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        }
        icon.setImageDrawable(drawable);
        row.addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 4, 0, 12, 0));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setTextColor(value == null ? Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, value == null ? 15 : 13);
        labelView.setText(label);
        textColumn.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (value != null) {
            TextView valueView = new TextView(context);
            valueView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valueView.setText(value);
            textColumn.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        row.addView(textColumn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
