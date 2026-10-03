package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Delegates.MemberRequestsDelegate;

public class MemberRequestsActivity extends BaseFragment {

    public static final int searchMenuItem = 0;
    public static final int approveAllMenuItem = 1;

    private final MemberRequestsDelegate delegate;

    public MemberRequestsActivity(long chatId) {
        delegate = new MemberRequestsDelegate(this, getLayoutContainer(), chatId, true) {
            @Override
            protected void onImportersChanged(String query, boolean fromCache, boolean fromHide) {
                if (fromHide) {
                    actionBar.setSearchFieldText("");
                } else {
                    super.onImportersChanged(query, fromCache, fromHide);
                }
                // show/hide "Approve All" icon when requests change
                ActionBarMenuItem approveItem = actionBar.createMenu().getItem(approveAllMenuItem);
                if (approveItem != null) {
                    approveItem.setVisibility(hasAllImporters() ? View.VISIBLE : View.GONE);
                }
            }
        };
    }

    @Override
    public View createView(Context context) {
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == approveAllMenuItem) {
                    delegate.approveAll();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(delegate.isChannel ? LocaleController.getString(R.string.SubscribeRequests) : LocaleController.getString(R.string.MemberRequests));

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem approveAllItem = menu.addItem(approveAllMenuItem, R.drawable.filled_requests_24);
        approveAllItem.setContentDescription(LocaleController.getString(R.string.ApproveAllRequests));
        approveAllItem.setVisibility(View.GONE);

        ActionBarMenuItem searchItem = menu.addItem(searchMenuItem, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        super.onSearchExpand();
                        delegate.setSearchExpanded(true);
                    }
                    @Override
                    public void onSearchCollapse() {
                        super.onSearchCollapse();
                        delegate.setSearchExpanded(false);
                        delegate.setQuery(null);
                    }
                    @Override
                    public void onTextChanged(EditText editText) {
                        super.onTextChanged(editText);
                        delegate.setQuery(editText.getText().toString());
                    }
                });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setVisibility(View.GONE);

        FrameLayout rootLayout = delegate.getRootLayout();
        actionBar.setAdaptiveBackground(delegate.getRecyclerView(), false);
        delegate.loadMembers();

        return fragmentView = rootLayout;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        return delegate.onBackPressed(invoked);
    }
}
