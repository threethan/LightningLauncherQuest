package com.threethan.launcher.adapter;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BaseInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.Icon;
import com.threethan.launcher.helper.Launch;
import com.threethan.launcher.helper.Platform;
import com.threethan.launcher.helper.Settings;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.lib.StringLib;
import com.threethan.launcher.support.AppDetailsDialog;
import com.threethan.launcher.support.SettingsManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
    AppsAdapter

    The adapter for app grid views. A separate instance is used for square and banner apps, as well
    as for each activity.

    Only app views matching the current search term or group filter will be shown.
    When a view is requested for an app, it gets cached, and will be reused if displayed again.
    This massively speeds up group-switching and makes search possible without immense lag.

    This class also handles clicking and long clicking apps, including the app settings dialog.
    It also handles displaying/updating the views of an app (hover interactions, background website)
 */
public class AppsAdapter extends ArrayListAdapter<ApplicationInfo, AppsAdapter.AppViewHolder> {
    private LauncherActivity launcherActivity;
    private Set<ApplicationInfo> fullAppSet;
    private boolean getEditMode() {
        return launcherActivity.isEditing();
    }
    public AppsAdapter(LauncherActivity activity) {
        launcherActivity = activity;
    }
    public void setFullAppSet(Set<ApplicationInfo> myApps) {
        fullAppSet = myApps;
    }
    public synchronized void setAppList(LauncherActivity activity) {
        SettingsManager settingsManager = SettingsManager.getInstance(activity);
        launcherActivity = activity;

        setItems(Collections.unmodifiableList(settingsManager
                .getVisibleApps(activity, settingsManager.getAppGroupsSorted(true), fullAppSet)));
    }

    public synchronized void filterBy(String text) {
        SettingsManager settingsManager = SettingsManager.getInstance(launcherActivity);
        final List<ApplicationInfo> newItems =
                settingsManager.getVisibleApps(launcherActivity, settingsManager.getAppGroupsSorted(false), fullAppSet);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            newItems.removeIf(item -> !StringLib.forSort(SettingsManager.getAppLabel(item)).contains(StringLib.forSort(text)));
        }

        // Add search queries
        if (!text.isEmpty() && !launcherActivity.isEditing()) {

            final ApplicationInfo googleProxy = new ApplicationInfo();
            googleProxy.packageName = StringLib.googleSearchForUrl(text);
            newItems.add(googleProxy);

            final ApplicationInfo youTubeProxy = new ApplicationInfo();
            youTubeProxy.packageName = StringLib.youTubeSearchForUrl(text);
            newItems.add(youTubeProxy);

            final ApplicationInfo apkPureProxy = new ApplicationInfo();
            apkPureProxy.packageName = StringLib.apkPureSearchForUrl(text);
            newItems.add(apkPureProxy);

            final ApplicationInfo apkMirrorProxy = new ApplicationInfo();
            apkMirrorProxy.packageName = StringLib.apkMirrorSearchForUrl(text);
            newItems.add(apkMirrorProxy);
        }

        setItems(newItems);
    }
    public void setLauncherActivity(LauncherActivity val) {
        launcherActivity = val;
    }

    protected static class AppViewHolder extends RecyclerView.ViewHolder {
        View view;
        ImageView imageView;
        ImageView imageViewSquare;
        ImageView imageViewBanner;
        View clip;
        View textSpacer;
        View bumpSpacer;
        TextView textView;
        Button moreButton;
        Button killButton;
        ApplicationInfo app;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public ApplicationInfo getItem(int position) { return items.get(position); }
    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = (LayoutInflater) launcherActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View itemView = layoutInflater.inflate(R.layout.lv_app, parent, false);
        AppViewHolder holder =  new AppViewHolder(itemView);
        itemView.findViewById(R.id.clip).setClipToOutline(true);

        holder.view = itemView;
        holder.imageViewSquare = itemView.findViewById(R.id.imageLabelSquare);
        holder.imageViewBanner = itemView.findViewById(R.id.imageLabelBanner);
        holder.clip = itemView.findViewById(R.id.clip);
        holder.textSpacer = itemView.findViewById(R.id.textSpacer);
        holder.bumpSpacer = itemView.findViewById(R.id.bumpSpacer);
        holder.textView = itemView.findViewById(R.id.textLabel);
        holder.moreButton = itemView.findViewById(R.id.moreButton);
        holder.killButton = itemView.findViewById(R.id.killButton);
        if (Platform.isTv()) holder.clip.setBackgroundResource(R.drawable.bkg_app_atv);

        setActions(holder);

        return holder;
    }
    private void setActions(AppViewHolder holder) {
        // Sub-buttons
        holder.moreButton.setOnClickListener(view -> AppDetailsDialog.showAppDetails(holder.app, launcherActivity));

        holder.killButton.setOnClickListener(view -> {
            SettingsManager.stopRunning(holder.app.packageName);
            view.setVisibility(View.GONE);
        });

        // Click
        holder.view.setOnClickListener(view -> {
            if (getEditMode()) {
                boolean selected = launcherActivity.selectApp(holder.app.packageName);
                holder.view.animate().alpha(selected ? 0.5f : 1).setDuration(150).start();
            } else {
                if (Launch.launchApp(launcherActivity, holder.app)) animateOpen(holder);

                boolean isWeb = App.isWebsite(holder.app);
                if (isWeb) holder.killButton.setVisibility(View.VISIBLE);
                shouldAnimateClose = isWeb || Platform.isTv(launcherActivity);
            }
        });
        holder.view.setOnLongClickListener(view -> {
            if (getEditMode() || !launcherActivity.canEdit() || launcherActivity.dataStoreEditor
                    .getBoolean(Settings.KEY_DETAILS_LONG_PRESS, Settings.DEFAULT_DETAILS_LONG_PRESS)) {
                if (holder.killButton.getVisibility() == View.VISIBLE)
                    holder.killButton.callOnClick();
                else AppDetailsDialog.showAppDetails(holder.app, launcherActivity);
            } else {
                launcherActivity.setEditMode(true);
                launcherActivity.selectApp(holder.app.packageName);
                holder.view.animate().alpha(0.5f).setDuration(300).start();
            }
            return true;
        });

        // Hover
        View.OnHoverListener hoverListener = (view, event) -> {
            boolean hovered;
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) hovered = true;
            else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                for (View subView : new View[] {holder.moreButton, holder.killButton})
                    if (view != subView && subView != null && subView.isHovered()) return false;
                hovered = false;
            } else return false;
            updateHover(holder, hovered);
            return false;
        };

        holder.view.setOnHoverListener(hoverListener);
        holder.view.setOnFocusChangeListener((view, hasFocus) -> updateHover(holder, hasFocus));

        holder.moreButton.setOnHoverListener(hoverListener);
        holder.killButton.setOnHoverListener(hoverListener);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        ApplicationInfo app = getItem(position);

        final boolean banner = App.isBanner(app);
        holder.imageViewSquare.setVisibility(banner ? View.GONE : View.VISIBLE);
        holder.imageViewBanner.setVisibility(banner ? View.VISIBLE : View.GONE);
        holder.imageView = banner ? holder.imageViewBanner : holder.imageViewSquare;

        holder.textView.setText(SettingsManager.getAppLabel(app));
        holder.textView.setTextColor(Color.parseColor(LauncherActivity.darkMode ? "#FFFFFF" : "#000000"));
        holder.textView.setShadowLayer(6, 0, 0, Color.parseColor(LauncherActivity.darkMode ? "#000000" : "#FFFFFF"));

        holder.textSpacer.setVisibility(!banner && LauncherActivity.namesSquare ? View.VISIBLE : View.GONE);

        // set value into textview
        if (banner && LauncherActivity.namesBanner || !banner && LauncherActivity.namesSquare) {
            holder.textView.setVisibility(View.VISIBLE);
            holder.textSpacer.setVisibility(View.VISIBLE);
            holder.bumpSpacer.setVisibility(View.VISIBLE);
            holder.textView.setTranslationY(launcherActivity.dp(2));
            holder.textView.setMaxLines(2);
        } else {
            holder.textView.setVisibility(View.GONE);
            holder.textSpacer.setVisibility(View.GONE);
            holder.bumpSpacer.setVisibility(View.GONE);
            holder.textView.setTranslationY(launcherActivity.dp(7));
            holder.textView.setMaxLines(1);
        }
        holder.app = app;

        //Load Icon
        Drawable appIcon = Icon.loadIcon(launcherActivity, holder.app, holder.imageView);
        launcherActivity.runOnUiThread(() -> holder.imageView.setImageDrawable(appIcon));

        updateSelected(holder);
    }

    public void notifySelectionChange(String packageName) {
        for (int i=0; i<items.size(); i++)
            if (Objects.equals(items.get(i).packageName, packageName))
                notifyItemChanged(i, items.get(i));
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    private void updateSelected(AppViewHolder holder) {
        boolean selected = launcherActivity.isSelected(holder.app.packageName);
        if (selected != holder.view.getAlpha() < 0.9) {
            ObjectAnimator an = ObjectAnimator.ofFloat(holder.view, "alpha", selected ? 0.5F : 1.0F);
            an.setDuration(150);
            an.start();
        }
        holder.killButton.setVisibility(SettingsManager.getRunning(holder.app.packageName) ? View.VISIBLE : View.GONE);
        // Top search result
        if (launcherActivity.currentTopSearchResult != null &&
                Objects.equals(
                        Icon.cacheName(launcherActivity.currentTopSearchResult),
                        Icon.cacheName(holder.app))) {
            updateHover(holder, true);
        } else if (launcherActivity.clearFocusPackageNames.contains(Icon.cacheName(holder.app))) {
            updateHover(holder, false);
            launcherActivity.clearFocusPackageNames.remove(Icon.cacheName(holder.app));
        }
    }
    public void updateHover(AppViewHolder holder, boolean hovered) {
        if (!Platform.isTv(launcherActivity))
            holder.moreButton.setVisibility(hovered ? View.VISIBLE : View.GONE);
        holder.killButton.setBackgroundResource(hovered
            ? R.drawable.ic_circ_running_kb
            : R.drawable.ic_running_ns);

        final boolean tv = Platform.isTv(launcherActivity);
        final float newScaleInner = hovered ? (tv ? 1.075f : 1.060f) : 1.005f;
        final float newScaleOuter = hovered ? (tv ? 1.270f : 1.075f) : 1.000f;
        final float newElevation = hovered ? (tv ? 15f : 20f) : 3f;
        final float textScale = 1-(1-(1/newScaleOuter))*0.7f;
        final int duration = tv ? 175 : 250;
        BaseInterpolator intepolator = tv ? new DecelerateInterpolator() : new OvershootInterpolator();

        holder.imageView.animate().scaleX(newScaleInner).scaleY(newScaleInner)
                .setDuration(duration).setInterpolator(intepolator).start();
        holder.view     .animate().scaleX(newScaleOuter).scaleY(newScaleOuter)
                .setDuration(duration).setInterpolator(intepolator).start();
        holder.moreButton.animate().alpha(hovered ? 1f : 0f)
                .setDuration(duration).setInterpolator(intepolator).start();
        holder.textView .animate().scaleX(textScale).scaleY(textScale)
                .setDuration(duration).setInterpolator(intepolator).start();

        ObjectAnimator aE = ObjectAnimator.ofFloat(holder.clip, "elevation", newElevation);
        aE.setDuration(duration).start();

        boolean banner = holder.imageView == holder.imageViewBanner;
        if (banner && !LauncherActivity.namesBanner || !banner && !LauncherActivity.namesSquare)
            holder.textView.setVisibility(hovered ? View.VISIBLE : View.GONE);

        // Force correct state, even if interrupted
        if (!hovered) {
            holder.view.postDelayed(() -> {
                holder.imageView.setScaleX(newScaleInner);
                holder.imageView.setScaleY(newScaleInner);
                holder.view.setScaleX(newScaleOuter);
                holder.view.setScaleY(newScaleOuter);
                holder.clip.setElevation(newElevation);
            }, tv ? 175 : 250);
        }
        holder.view.setZ(hovered ? 2 : 1);
    }
    @Override
    public int getItemViewType(int position) {
        return App.isBanner(items.get(position)) ? 2 : 1;
    }

    // Animation

    private void animateOpen(AppViewHolder holder) {

        int[] l = new int[2];
        View clip = holder.view.findViewById(R.id.clip);
        clip.getLocationInWindow(l);
        int w = clip.getWidth();
        int h = clip.getHeight();

        View openAnim = launcherActivity.rootView.findViewById(R.id.openAnim);
        openAnim.setX(l[0] + (Platform.isTv() ? 30 : 0));
        openAnim.setY(l[1] + (Platform.isTv() ? 30 : 0));
        ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(w, h);
        openAnim.setLayoutParams(layoutParams);

        openAnim.setVisibility(View.VISIBLE);
        openAnim.setAlpha(1F);
        openAnim.setClipToOutline(true);
        openAnim.setScaleX(Platform.isTv() ? 1.40F : 1.08F);
        openAnim.setScaleY(Platform.isTv() ? 1.40F : 1.08F);

        ImageView animIcon = openAnim.findViewById(R.id.openIcon);
        ImageView animIconBg = openAnim.findViewById(R.id.openIconBg);
        animIcon.setImageDrawable(holder.imageView.getDrawable());
        animIconBg.setImageDrawable(holder.imageView.getDrawable());
        animIconBg.setAlpha(1f);

        View openProgress = launcherActivity.rootView.findViewById(R.id.openProgress);
        openProgress.setVisibility(View.VISIBLE);
        openProgress.setAlpha(0f);

        ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", 100f);
        ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", 100f);
        ObjectAnimator aA = ObjectAnimator.ofFloat(animIcon, "Alpha", 0f);
        ObjectAnimator aP = ObjectAnimator.ofFloat(openProgress, "Alpha", 0.8f);
        ObjectAnimator aPo = ObjectAnimator.ofFloat(openProgress, "Alpha", 0.0f);
        aX.setDuration(1000);
        aY.setDuration(1000);
        aA.setDuration(500);
        aP.setDuration(500);
        aPo.setDuration(3000);
        aP.setStartDelay(1000);
        aPo.setStartDelay(7000);
        aX.start();
        aY.start();
        aA.start();
        aP.start();
        aPo.start();
    }
    public static boolean shouldAnimateClose = false;
    public static boolean animateClose(LauncherActivity launcherActivity) {
        View openAnim = launcherActivity.rootView.findViewById(R.id.openAnim);
        ImageView animIcon = openAnim.findViewById(R.id.openIcon);

        View openProgress = launcherActivity.rootView.findViewById(R.id.openProgress);
        openProgress.setVisibility(View.INVISIBLE);

        final boolean rv = (openAnim.getVisibility() == View.VISIBLE);
        if (!AppsAdapter.shouldAnimateClose) {
            openAnim.setVisibility(View.INVISIBLE);
            openAnim.setScaleX(1);
            openAnim.setScaleY(1);
            animIcon.setAlpha(1.0f);
            openAnim.setVisibility(View.INVISIBLE);
        } else {
            // This animation assumes the app already animated open, and therefore does not set icon
            ImageView animIconBg = openAnim.findViewById(R.id.openIconBg);

            openAnim.setScaleX(3f);
            openAnim.setScaleY(3f);
            animIcon.setAlpha(1f);

            ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", 1.08f);
            ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", 1.08f);
            ObjectAnimator aA = ObjectAnimator.ofFloat(animIconBg, "Alpha", 0f);

            aX.setDuration(100);
            aY.setDuration(100);
            aA.setDuration(50);
            aA.setStartDelay(50);
            aA.start();
            aX.start();
            aY.start();

            openAnim.postDelayed(() -> openAnim.setVisibility(View.INVISIBLE), 100);
            shouldAnimateClose = false;
        }
        return rv;
    }

    @Override
    public void notifyItemChanged(ApplicationInfo app) {
        if (items != null && items.contains(app)) {
            int i = items.indexOf(app);
            int j = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                j = sortIntoList(items, app, Comparator.comparing(item -> StringLib.forSort(SettingsManager.getAppLabel(item))));
            }
            if (i != j) notifyItemMoved(i, j);
        }
    }


    public void notifyAllChanged() {
        notifyItemRangeChanged(0, getItemCount());
    }

    // Uses binary tree search to find where an item belongs in a list which is otherwise sorted
    // It's an asynchronous sorting algorithm!
    private <T> int sortIntoList(List<T> list, T changedItem, Comparator<T> c) {
        list.remove(changedItem);
        int firstIndex = 0;
        int lastIndex = list.size()-1;
        int prevIndex = -1;
        while (true) {
            final int currentIndex = (firstIndex+lastIndex)/2;
            final int res = c.compare(changedItem, list.get(currentIndex));
            if (res == 0 || currentIndex == prevIndex) {
                list.add(currentIndex, changedItem);
                return currentIndex;
            }
            if (res > 0) firstIndex = currentIndex;
            else         lastIndex  = currentIndex;
            prevIndex = currentIndex;
        }
    }
}