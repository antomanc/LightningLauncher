package com.threethan.launcher.ui;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.threethan.launcher.ImageUtils;
import com.threethan.launcher.MainActivity;
import com.threethan.launcher.R;
import com.threethan.launcher.SettingsProvider;
import com.threethan.launcher.platforms.AbstractPlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** @noinspection deprecation, rawtypes */
class IconTask extends AsyncTask {
    @SuppressLint("StaticFieldLeak")
    private ImageView imageView;
    @SuppressLint("StaticFieldLeak")
    private ImageView imageViewBg;
    private Drawable appIcon;

    @Override
    protected Object doInBackground(Object[] objects) {
        final ApplicationInfo currentApp = (ApplicationInfo) objects[1];
        final MainActivity mainActivityContext = (MainActivity) objects[2];
        final AbstractPlatform appPlatform = AbstractPlatform.getPlatform(currentApp);
        imageView = (ImageView) objects[3];
        imageViewBg = (ImageView) objects[4];

        try {
            ImageView[] imageViews = {imageView, imageViewBg};
            appIcon = appPlatform.loadIcon(mainActivityContext, currentApp, imageViews);
        } catch (Resources.NotFoundException | PackageManager.NameNotFoundException e) {
            Log.d("DreamGrid", "Error loading icon for app: " + currentApp.packageName, e);
        }
        return null;
    }
    @Override
    protected void onPostExecute(Object _n) {
        imageView.setImageDrawable(appIcon);
        imageViewBg.setImageDrawable(appIcon);
    }
}

public class AppsAdapter extends BaseAdapter{
    private static Drawable iconDrawable;
    private static File iconFile;
    private static String packageName;
    private final MainActivity mainActivity;
    private final List<ApplicationInfo> appList;
    private final boolean isEditMode;
    private final boolean showTextLabels;
    private final SettingsProvider settingsProvider;

    public AppsAdapter(MainActivity context, boolean editMode, boolean names, List<ApplicationInfo> allApps) {
        mainActivity = context;
        isEditMode = editMode;
        showTextLabels = names;
        settingsProvider = SettingsProvider.getInstance(mainActivity);

        ArrayList<String> sortedGroups = settingsProvider.getAppGroupsSorted(false, mainActivity);
        ArrayList<String> sortedSelectedGroups = settingsProvider.getAppGroupsSorted(true, mainActivity);
        boolean isFirstGroupSelected = !sortedSelectedGroups.isEmpty() && !sortedGroups.isEmpty() && sortedSelectedGroups.get(0).compareTo(sortedGroups.get(0)) == 0;
        appList = settingsProvider.getInstalledApps(context, sortedSelectedGroups, isFirstGroupSelected, allApps);
    }

    private static class ViewHolder {
        LinearLayout layout;
        ImageView imageView;
        ImageView imageViewBg;
        TextView textView;
        Button moreButton;
    }

    public int getCount() { return appList.size(); }

    public Object getItem(int position) {
        return appList.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    /** @noinspection deprecation*/
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        final ApplicationInfo currentApp = appList.get(position);
        LayoutInflater layoutInflater = (LayoutInflater) mainActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the layout
            int layout = AbstractPlatform.isWideApp(currentApp, mainActivity) ? R.layout.lv_app_wide : R.layout.lv_app;

            convertView = layoutInflater.inflate(layout, parent, false);
            holder = new ViewHolder();
            holder.layout = convertView.findViewById(R.id.layout);
            holder.imageView = convertView.findViewById(R.id.imageLabel);
            holder.imageViewBg = convertView.findViewById(R.id.imageLabelBg);
            holder.textView = convertView.findViewById(R.id.textLabel);
            holder.moreButton = convertView.findViewById(R.id.moreButton);

            // Set clipToOutline to true on imageView
            convertView.findViewById(R.id.clip).setClipToOutline(true);

            convertView.setTag(holder);

            if (position == 0) {
                mainActivity.updateGridViewHeights();
            }

        } else {
            // ViewHolder already exists, reuse it
            holder = (ViewHolder) convertView.getTag();
        }

        // set value into textview
        PackageManager packageManager = mainActivity.getPackageManager();
        String name = SettingsProvider.getAppDisplayName(mainActivity, currentApp.packageName, currentApp.loadLabel(packageManager));
        holder.textView.setText(name);
        holder.textView.setVisibility(showTextLabels ? View.VISIBLE : View.GONE);

        if (isEditMode) {
            // short click for app details, long click to activate drag and drop
            holder.layout.setOnTouchListener((view, motionEvent) -> {
                {
                    boolean selected = mainActivity.selectApp(currentApp.packageName);
                    view.setAlpha(selected? 0.5F : 1.0F);
                }
                return false;
            });
        } else {
            holder.layout.setOnClickListener(view -> {
                if (!(SettingsProvider.getAppLaunchOut(currentApp.packageName) || AbstractPlatform.isVirtualRealityApp(currentApp, mainActivity))) animateOpen(holder);
                mainActivity.openApp(currentApp);
            });
            holder.layout.setOnLongClickListener(view -> {
                try {
                    showAppDetails(currentApp);
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }
                return false;
            });
        }
        holder.moreButton.setOnClickListener(view -> {
            try {
                showAppDetails(currentApp);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        new IconTask().execute(this, currentApp, mainActivity, holder.imageView, holder.imageViewBg);

        return convertView;
    }

    public void onImageSelected(String path, ImageView selectedImageView) {
        AbstractPlatform.clearIconCache();
        if (path != null) {
            Bitmap bitmap = ImageUtils.getResizedBitmap(BitmapFactory.decodeFile(path), 450);
            ImageUtils.saveBitmap(bitmap, iconFile);
            selectedImageView.setImageBitmap(bitmap);
        } else {
            selectedImageView.setImageDrawable(iconDrawable);
            AbstractPlatform.updateIcon(iconFile, packageName, null);
            //No longer sets icon here but that should be fine
        }
    }

    private void showAppDetails(ApplicationInfo currentApp) throws PackageManager.NameNotFoundException {
        // set layout
        AlertDialog appDetailsDialog = new AlertDialog.Builder(mainActivity).setView(R.layout.dialog_app_details).create();
        Objects.requireNonNull(appDetailsDialog.getWindow()).setBackgroundDrawableResource(R.drawable.bkg_dialog);
        appDetailsDialog.show();

        // info action
        appDetailsDialog.findViewById(R.id.info).setOnClickListener(view -> mainActivity.openAppDetails(currentApp.packageName));
        appDetailsDialog.findViewById(R.id.uninstall).setOnClickListener(view -> mainActivity.uninstallApp(currentApp.packageName));

        // toggle launch mode
        final boolean[] launchOut = {SettingsProvider.getAppLaunchOut(currentApp.packageName)};
        final Button launchModeBtn = appDetailsDialog.findViewById(R.id.launch_mode);
        final boolean isVr = AbstractPlatform.isVirtualRealityApp(currentApp, mainActivity);
        if (isVr) { //VR apps MUST launch out, so just hide the option
            launchModeBtn.setVisibility(View.GONE);
        } else {
            launchModeBtn.setVisibility(View.VISIBLE);
            launchModeBtn.setText(mainActivity.getString(launchOut[0] ? R.string.launch_out : R.string.launch_in));

            appDetailsDialog.findViewById(R.id.launch_mode).setOnClickListener(view -> {

                if (mainActivity.sharedPreferences.getBoolean(SettingsProvider.KEY_SEEN_LAUNCH_OUT_POPUP, false)) {
                    // Actually set
                    SettingsProvider.setAppLaunchOut(currentApp.packageName, !launchOut[0]);
                    launchOut[0] = SettingsProvider.getAppLaunchOut(currentApp.packageName);
                    launchModeBtn.setText(mainActivity.getString(launchOut[0] ? R.string.launch_out : R.string.launch_in));
                    return;
                }
                AlertDialog subDialog = new AlertDialog.Builder(mainActivity).setView(R.layout.dialog_launch_out_info).create();
                Objects.requireNonNull(subDialog.getWindow()).setBackgroundDrawableResource(R.drawable.bkg_dialog);
                subDialog.show();

                subDialog.findViewById(R.id.ok).setOnClickListener(view1 -> {
                    SharedPreferences.Editor editor = mainActivity.sharedPreferences.edit();
                    editor.putBoolean(SettingsProvider.KEY_SEEN_LAUNCH_OUT_POPUP, true).apply();
                    editor.apply();
                    subDialog.dismiss();
                    // Actually set
                    SettingsProvider.setAppLaunchOut(currentApp.packageName, !launchOut[0]);
                    launchOut[0] = SettingsProvider.getAppLaunchOut(currentApp.packageName);
                    launchModeBtn.setText(mainActivity.getString(launchOut[0] ? R.string.launch_out : R.string.launch_in));
                });
                subDialog.findViewById(R.id.cancel).setOnClickListener(view1 -> subDialog.dismiss());
            });
        }

        // set name
        PackageManager packageManager = mainActivity.getPackageManager();
        String name = SettingsProvider.getAppDisplayName(mainActivity, currentApp.packageName, currentApp.loadLabel(packageManager));
        final EditText appNameEditText = appDetailsDialog.findViewById(R.id.app_name);
        appNameEditText.setText(name);
        appDetailsDialog.findViewById(R.id.cancel).setOnClickListener(view -> {
            appDetailsDialog.dismiss();

            settingsProvider.setAppDisplayName(mainActivity, currentApp, appNameEditText.getText().toString());
            mainActivity.reloadUI();
        });

        // load icon
        ImageView tempImage = appDetailsDialog.findViewById(R.id.app_icon);
        AbstractPlatform appPlatform = AbstractPlatform.getPlatform(currentApp);
        tempImage.setImageDrawable(appPlatform.loadIcon(mainActivity, currentApp, null));

        tempImage.setClipToOutline(true);

        tempImage.setOnClickListener(iconPickerView -> {
            iconDrawable = currentApp.loadIcon(packageManager);
            packageName = currentApp.packageName;

            final boolean isWide = AbstractPlatform.isWideApp(currentApp, mainActivity);
            iconFile = AbstractPlatform.packageToPath(mainActivity, currentApp.packageName, isWide);
            if (iconFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                iconFile.delete();
            }
            mainActivity.setSelectedImageView(tempImage);
            ImageUtils.showImagePicker(mainActivity, MainActivity.PICK_ICON_CODE);
        });
    }

    private void animateOpen(ViewHolder holder) {

        int[] l = new int[2];
        View clip = holder.layout.findViewById(R.id.clip);
        clip.getLocationInWindow(l);
        int w = clip.getWidth();
        int h = clip.getHeight();

        View openAnim = mainActivity.findViewById(R.id.openAnim);
        openAnim.setX(l[0]);
        openAnim.setY(l[1]);
        ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(w, h);
        openAnim.setLayoutParams(layoutParams);

        openAnim.setVisibility(View.VISIBLE);

        openAnim.setClipToOutline(true);


        ImageView animIcon = openAnim.findViewById(R.id.openIcon);
        ImageView animIconBg = openAnim.findViewById(R.id.openIconBg);
        animIcon.setImageDrawable(holder.imageView.getDrawable());
        animIconBg.setImageDrawable(holder.imageView.getDrawable());

        View openProgress = mainActivity.findViewById(R.id.openProgress);
        openProgress.setVisibility(View.VISIBLE);

        ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", 100f);
        ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", 100f);
        ObjectAnimator aA = ObjectAnimator.ofFloat(animIcon, "Alpha", 0f);
        ObjectAnimator aP = ObjectAnimator.ofFloat(openProgress, "Alpha", 0.8f);
        aX.setDuration(1000);
        aY.setDuration(1000);
        aA.setDuration(500);
        aP.setDuration(500);
        aP.setStartDelay(1000);
        aX.start();
        aY.start();
        aA.start();
        aP.start();
    }
}