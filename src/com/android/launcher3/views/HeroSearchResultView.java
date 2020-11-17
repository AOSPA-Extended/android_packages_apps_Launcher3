/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.views;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.search.AllAppsSearchBarController.SearchTargetHandler;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A view representing a high confidence app search result that includes shortcuts
 * TODO (sfufa@) consolidate this with SearchResultIconRow
 */
public class HeroSearchResultView extends LinearLayout implements DragSource, SearchTargetHandler {

    public static final String TARGET_TYPE_HERO_APP = "hero_app";

    public static final int MAX_SHORTCUTS_COUNT = 2;

    private SearchTarget mSearchTarget;
    private BubbleTextView mBubbleTextView;
    private View mIconView;
    private BubbleTextView[] mDeepShortcutTextViews = new BubbleTextView[2];


    public HeroSearchResultView(Context context) {
        super(context);
    }

    public HeroSearchResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeroSearchResultView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Launcher launcher = Launcher.getLauncher(getContext());
        DeviceProfile grid = launcher.getDeviceProfile();
        mIconView = findViewById(R.id.icon);
        ViewGroup.LayoutParams iconParams = mIconView.getLayoutParams();
        iconParams.height = grid.allAppsIconSizePx;
        iconParams.width = grid.allAppsIconSizePx;


        mBubbleTextView = findViewById(R.id.bubble_text);
        mBubbleTextView.setOnClickListener(view -> {
            handleSelection(SearchTargetEvent.SELECT);
            launcher.getItemOnClickListener().onClick(view);
        });
        mBubbleTextView.setOnLongClickListener(new HeroItemDragHandler(getContext(), this));


        mDeepShortcutTextViews[0] = findViewById(R.id.shortcut_0);
        mDeepShortcutTextViews[1] = findViewById(R.id.shortcut_1);
        for (BubbleTextView bubbleTextView : mDeepShortcutTextViews) {
            bubbleTextView.setLayoutParams(
                    new LinearLayout.LayoutParams(grid.allAppsIconSizePx,
                            grid.allAppsIconSizePx));
            bubbleTextView.setOnClickListener(view -> {
                WorkspaceItemInfo itemInfo = (WorkspaceItemInfo) bubbleTextView.getTag();
                SearchTargetEvent event = new SearchTargetEvent.Builder(mSearchTarget,
                        SearchTargetEvent.CHILD_SELECT).setShortcutPosition(itemInfo.rank).build();
                SearchEventTracker.getInstance(getContext()).notifySearchTargetEvent(event);
                launcher.getItemOnClickListener().onClick(view);
            });
        }
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        AllAppsStore apps = Launcher.getLauncher(getContext()).getAppsView().getAppsStore();
        AppInfo appInfo = apps.getApp(new ComponentKey(searchTarget.getComponentName(),
                searchTarget.getUserHandle()));
        List<ShortcutInfo> infos = mSearchTarget.getShortcutInfos();

        ArrayList<Pair<ShortcutInfo, ItemInfoWithIcon>> shortcuts = new ArrayList<>();
        for (int i = 0; infos != null && i < infos.size() && i < MAX_SHORTCUTS_COUNT; i++) {
            ShortcutInfo shortcutInfo = infos.get(i);
            ItemInfoWithIcon si = new WorkspaceItemInfo(shortcutInfo, getContext());
            si.rank = i;
            shortcuts.add(new Pair<>(shortcutInfo, si));
        }

        mBubbleTextView.applyFromApplicationInfo(appInfo);
        mIconView.setBackground(mBubbleTextView.getIcon());
        mIconView.setTag(appInfo);
        LauncherAppState appState = LauncherAppState.getInstance(getContext());
        for (int i = 0; i < mDeepShortcutTextViews.length; i++) {
            BubbleTextView shortcutView = mDeepShortcutTextViews[i];
            mDeepShortcutTextViews[i].setVisibility(shortcuts.size() > i ? VISIBLE : GONE);
            if (i < shortcuts.size()) {
                Pair<ShortcutInfo, ItemInfoWithIcon> p = shortcuts.get(i);
                //apply ItemInfo and prepare view
                shortcutView.applyFromWorkspaceItem((WorkspaceItemInfo) p.second);
                MODEL_EXECUTOR.execute(() -> {
                    // load unbadged shortcut in background and update view when icon ready
                    appState.getIconCache().getUnbadgedShortcutIcon(p.second, p.first);
                    MAIN_EXECUTOR.post(() -> shortcutView.reapplyItemInfo(p.second));
                });
            }
        }
        SearchEventTracker.INSTANCE.get(getContext()).registerWeakHandler(searchTarget, this);
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        mBubbleTextView.setVisibility(VISIBLE);
        mBubbleTextView.setIconVisible(true);
    }

    private void setWillDrawIcon(boolean willDraw) {
        mIconView.setVisibility(willDraw ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Drag and drop handler for popup items in Launcher activity
     */
    public static class HeroItemDragHandler implements OnLongClickListener {
        private final Launcher mLauncher;
        private final HeroSearchResultView mContainer;

        HeroItemDragHandler(Context context, HeroSearchResultView container) {
            mLauncher = Launcher.getLauncher(context);
            mContainer = container;
        }

        @Override
        public boolean onLongClick(View v) {
            if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
            mContainer.setWillDrawIcon(false);

            DraggableView draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON);
            WorkspaceItemInfo itemInfo = new WorkspaceItemInfo((AppInfo) v.getTag());
            itemInfo.container = CONTAINER_ALL_APPS;
            DragPreviewProvider previewProvider = new ShortcutDragPreviewProvider(
                    mContainer.mIconView, new Point());
            mLauncher.getWorkspace().beginDragShared(mContainer.mBubbleTextView,
                    draggableView, mContainer, itemInfo, previewProvider, new DragOptions());

            SearchTargetEvent event = new SearchTargetEvent.Builder(mContainer.mSearchTarget,
                    SearchTargetEvent.LONG_PRESS).build();
            SearchEventTracker.INSTANCE.get(mLauncher).notifySearchTargetEvent(event);
            return false;
        }
    }

    @Override
    public void handleSelection(int eventType) {
        ItemInfo itemInfo = (ItemInfo) mBubbleTextView.getTag();
        if (itemInfo == null) return;
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.startActivitySafely(this, itemInfo.getIntent(), itemInfo);

        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }
}
