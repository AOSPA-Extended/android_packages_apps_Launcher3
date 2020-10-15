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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.ArrayList;

/**
 * A row of tappable TextViews with a breadcrumb for settings search.
 */
public class SearchSettingsRowView extends LinearLayout implements
        View.OnClickListener, AllAppsSearchBarController.PayloadResultHandler<Bundle> {

    private TextView mTitleView;
    private TextView mDescriptionView;
    private TextView mBreadcrumbsView;
    private Intent mIntent;
    private AllAppsSearchPlugin mPlugin;

    public SearchSettingsRowView(@NonNull Context context) {
        super(context);
    }

    public SearchSettingsRowView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchSettingsRowView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitleView = findViewById(R.id.title);
        mDescriptionView = findViewById(R.id.description);
        mBreadcrumbsView = findViewById(R.id.breadcrumbs);
        setOnClickListener(this);
    }

    @Override
    public void applyAdapterInfo(
            AllAppsGridAdapter.AdapterItemWithPayload<Bundle> adapterItemWithPayload) {
        Bundle bundle = adapterItemWithPayload.getPayload();
        mPlugin = adapterItemWithPayload.getPlugin();
        mIntent = bundle.getParcelable("intent");
        showIfAvailable(mTitleView, bundle.getString("title"));
        showIfAvailable(mDescriptionView, bundle.getString("description"));
        ArrayList<String> breadcrumbs = bundle.getStringArrayList("breadcrumbs");
        //TODO: implement RTL friendly breadcrumbs view
        showIfAvailable(mBreadcrumbsView, breadcrumbs != null
                ? String.join(" > ", breadcrumbs) : null);
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
    }

    private void showIfAvailable(TextView view, @Nullable String string) {
        if (TextUtils.isEmpty(string)) {
            view.setVisibility(GONE);
        } else {
            view.setVisibility(VISIBLE);
            view.setText(string);
        }
    }

    @Override
    public void onClick(View view) {
        handleSelection(SearchTargetEvent.SELECT);
    }

    private void handleSelection(int eventType) {
        if (mIntent == null) return;
        // TODO: create ItemInfo object and then use it to call startActivityForResult for proper
        //  WW logging
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.startActivityForResult(mIntent, 0);

        SearchTargetEvent searchTargetEvent = new SearchTargetEvent(
                SearchTarget.ItemType.SETTINGS_ROW, eventType);
        searchTargetEvent.bundle = new Bundle();
        searchTargetEvent.bundle.putParcelable("intent", mIntent);
        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(searchTargetEvent);
        }
    }
}
