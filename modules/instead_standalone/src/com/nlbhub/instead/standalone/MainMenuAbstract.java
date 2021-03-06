package com.nlbhub.instead.standalone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Messenger;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import com.google.android.vending.expansion.downloader.*;
import com.nlbhub.instead.PropertyManager;
import com.nlbhub.instead.R;
import com.nlbhub.instead.STEADActivity;
import com.nlbhub.instead.standalone.expansion.APKHelper;
import com.nlbhub.instead.standalone.fs.SystemPathResolver;

import java.io.*;
import java.util.*;

/**
 * Created by Antokolos on 14.10.15.
 */
public abstract class MainMenuAbstract extends ListActivity implements SimpleAdapter.ViewBinder, IDownloaderClient {

    protected boolean onpause = false;
    protected boolean dwn = false;
    protected ProgressDialog dialog;
    protected static final String BR = "<br>";
    protected static final String LIST_TEXT = "list_text";

    private IStub mDownloaderClientStub;
    private IDownloaderService mRemoteService;
    private ProgressDialog mProgressDialog;

    protected class ListItem {
        public String text;
        public int icon;
    }

    private void initDownloaderClientStub(Context context) {
        try {
            mDownloaderClientStub = new APKHelper(context).createDownloaderStubIfNeeded((InsteadApplication) getApplication(), this);
            if (mDownloaderClientStub != null) {
                // Shows download progress
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setMessage(getResources().getString(R.string.downloading_assets));
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(InsteadApplication.ApplicationName, "Error creating downloader stub", e);
        }
    }

    @Override
    public void onServiceConnected(Messenger m) {
        mRemoteService = DownloaderServiceMarshaller.CreateProxy(m);
        mRemoteService.onClientUpdated(mDownloaderClientStub.getMessenger());
        mRemoteService.setDownloadFlags(IDownloaderService.FLAGS_DOWNLOAD_OVER_CELLULAR);
    }

    @Override
    public void onDownloadProgress(DownloadProgressInfo progress) {
        long percents = progress.mOverallProgress * 100 / progress.mOverallTotal;
        Log.v(InsteadApplication.ApplicationName, "DownloadProgress:"+Long.toString(percents) + "%");
        mProgressDialog.setProgress((int) percents);
    }

    @Override
    public void onDownloadStateChanged(int newState) {
        Log.v(InsteadApplication.ApplicationName, "DownloadStateChanged : " + getString(Helpers.getDownloaderStringResourceIDFromState(newState)));

        switch (newState) {
            case STATE_DOWNLOADING:
                Log.v(InsteadApplication.ApplicationName, "Downloading...");
                break;
            case STATE_COMPLETED: // The download was finished
                // validateXAPKZipFiles();
                mProgressDialog.setMessage(getResources().getString(R.string.preparing_assets));
                // dismiss progress dialog
                mProgressDialog.dismiss();

                break;
            case STATE_FAILED_UNLICENSED:
            case STATE_FAILED_FETCHING_URL:
            case STATE_FAILED_SDCARD_FULL:
            case STATE_FAILED_CANCELED:
            case STATE_FAILED:
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle(getResources().getString(R.string.dataerror));
                alert.setMessage(getResources().getString(R.string.download_failed));
                alert.setNeutralButton(getResources().getString(R.string.close), null);
                alert.show();
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler(this));
        // The following line is to workaround AndroidRuntimeException: requestFeature() must be called before adding content
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        dialog = new ProgressDialog(this);
        dialog.setTitle(getString(R.string.wait));
        dialog.setMessage(getString(R.string.init));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        setContentView(R.layout.mnhead);
        ListView listView = getListView();
        registerForContextMenu(listView);
        showMenu();
        if (!dwn) {
            checkRC();
        }
        initDownloaderClientStub(this);
    }

    @Override
    public boolean setViewValue(View view, Object data, String stringRepresetation) {
        ListItem listItem = (ListItem) data;

        TextView menuItemView = (TextView) view;
        menuItemView.setText(Html.fromHtml(listItem.text));
        menuItemView.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(listItem.icon), null, null, null);
        return true;
    }

    protected String getHtmlTagForName(String s) {
        return "<b>" + s + "</b>";
    }

    protected String getHtmlTagForSmall(String s) {
        return "<small><i>" + s + "</i></small>";
    }

    protected void showMenu() {
        showMenu(new ArrayList<Map<String, ListItem>>());
    }

    protected abstract void showMenu(List<Map<String, ListItem>> additionalListData);

    protected Map<String, ListItem> addListItem(String s, int i) {
        Map<String, ListItem> iD = new HashMap<String, ListItem>();
        ListItem l = new ListItem();
        l.text = s;
        l.icon = i;
        iD.put(LIST_TEXT, l);
        return iD;
    }

    protected void startAppAlt() {
        if (checkInstall()) {
            Intent myIntent = new Intent(this, STEADActivity.class);
            startActivity(myIntent);
        } else {
            checkRC();
        }
    }


    public boolean checkInstall() {
        return checkInstall(this);
    }

    public static boolean checkInstall(Context context) {
        SystemPathResolver dataResolver = new SystemPathResolver("data", context);

        BufferedReader input = null;
        try {
            String path = dataResolver.getPath() + StorageResolver.DataFlag;
            input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(path)), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return false;
        } catch (FileNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        String line;

        try {

            line = input.readLine();
            try {
                if (line.toLowerCase().matches(
                        ".*" + InsteadApplication.AppVer(context).toLowerCase() + ".*")) {
                    input.close();
                    return true;
                }
            } catch (NullPointerException e) {
                return false;
            } catch (IOException e) {
                return false;
            }
            ;

        } catch (IOException e) {
            return false;
        }
        try {
            input.close();
        } catch (IOException e) {
            return false;
        }

        return false;
    }

    protected abstract void deleteAdditionalAssets();

    protected abstract void copyAdditionalAssets();

    protected void checkRC() {
        if (!checkInstall()) {
            deleteAdditionalAssets();
            showMenu();
            loadData();
        }
        copyAdditionalAssets();
    }

    protected void loadData() {
        dwn = true;
        ShowDialog(getString(R.string.init));
        new DataDownloader(this, dialog);
    }

    public void ShowDialog(String m) {
        dialog.setMessage(m);
        dialog.setCancelable(false);
        dialog.show();
    }

    @Override
    protected void onPause() {
        onpause = true;
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
        super.onPause();
    }

    public void setDownGood() {
        dwn = false;
    }

    public void onError(String s) {
        dialog.setCancelable(true);
        dwn = false;
        Log.e("Instead-NG ERROR: ", s);
    }

    public void showRun() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
        dwn = false;
        checkRC();
    }

    public boolean isOnpause() {
        return onpause;
    }

    @Override
    protected void onResume() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.connect(this);
        }
        super.onResume();
        if (!dwn) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            checkRC();
        } else {
            if (onpause && !dialog.isShowing()) {
                dialog.show();
            }
        }
        onpause = false;
    }

    @Override
    protected void onStop() {
        if (null != mDownloaderClientStub) {
            mDownloaderClientStub.disconnect(this);
        }
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("onpause", onpause);
        savedInstanceState.putBoolean("dwn", dwn);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        dwn = savedInstanceState.getBoolean("dwn");
        onpause = savedInstanceState.getBoolean("onpause");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return true;
    }
}
