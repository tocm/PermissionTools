/*
 * Copyright (c)
*/
package com.tools.permissionlib;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.List;


/**
 * <p>Android 6.0 Permission helper</p>
 * Created by Andy.chen on 2017/3/6.
 */

public class PermissionUtils {

    private static final String TAG = PermissionUtils.class.getSimpleName();
    private final static int PERMISSION_CODE = 0x100;


    private final static int PERMISSION_REQUEST_OPEN_APPLICATION_SETTINGS_CODE = 0x200;

    //	private static PermissionUtils permissionUtils = null;
    private Activity mActivity;
    private IAppPermissionListener mIAppPermissionListener;

    /**
     * 所需要向用户申请的权限列表
     */
    private PermissionEntity[] mPermissionEntities;


    public PermissionUtils(Activity activity, IAppPermissionListener listener) {
        mActivity = activity;
        mIAppPermissionListener = listener;
        String permissions[] = getManifestPermissions(activity);
        if(permissions != null) {
            mPermissionEntities = new PermissionEntity[permissions.length];
            for (int i=0; i<permissions.length; i++) {
                String permission = permissions[i];
                PermissionEntity permissionEntity = new PermissionEntity(permission, permission, "应用需要获取权限: "+permission, PERMISSION_CODE + i);
                mPermissionEntities[i] = permissionEntity;
            }
        }
        applyPermissions();
    }


    /**
     * Android 6.0+上运行时申请权限
     */
    private void applyPermissions() {
        try {
            for (final PermissionEntity model : mPermissionEntities) {
                if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(mActivity, model.permission)) {
                    ActivityCompat.requestPermissions(mActivity, new String[]{model.permission}, model.requestCode);
                    return;
                }
            }
            if (mIAppPermissionListener != null) {
                mIAppPermissionListener.getPermissionSuccess();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private boolean checkRequestPermissionResultCode(int requestCode) {
        if (mPermissionEntities != null) {
            for (PermissionEntity entity : mPermissionEntities) {
                int reqCode = entity.requestCode;
                if (reqCode == requestCode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code onRequestPermissionsResult(...)}
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (checkRequestPermissionResultCode(requestCode)) {
            // 如果用户不允许，我们视情况发起二次请求或者引导用户到应用页面手动打开
            if (PackageManager.PERMISSION_GRANTED != grantResults[0]) {
                // 二次请求，表现为：以前请求过这个权限，但是用户拒接了
                // 在二次请求的时候，会有一个“不再提示的”checkbox
                // 因此这里需要给用户解释一下我们为什么需要这个权限，否则用户可能会永久不在激活这个申请
                // 方便用户理解我们为什么需要这个权限
                if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permissions[0])) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                    builder.setMessage(findPermissionExplain(permissions[0])).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            applyPermissions();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();


                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                    builder.setMessage(mActivity.getString(R.string.dialog_alert_msg) + findPermissionName(permissions[0])).setPositiveButton("settings",  new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            openOsSettings(PERMISSION_REQUEST_OPEN_APPLICATION_SETTINGS_CODE);
                        }
                    }).setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            if (mIAppPermissionListener != null) {
                                mIAppPermissionListener.getPermissionFailed();
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                return;
            }

            // 到这里就表示用户允许了本次请求，我们继续检查是否还有待申请的权限没有申请
            if (isAllRequestedPermissionGranted()) {
                if (mIAppPermissionListener != null) {
                    mIAppPermissionListener.getPermissionSuccess();
                }
            } else {
                applyPermissions();
            }
        }
    }

    /**
     * 对应Activity的 {@code onActivityResult(...)} 方法
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PERMISSION_REQUEST_OPEN_APPLICATION_SETTINGS_CODE:
                if (isAllRequestedPermissionGranted()) {
                    if (mIAppPermissionListener != null) {
                        mIAppPermissionListener.getPermissionSuccess();
                    }
                } else {
                    if (mIAppPermissionListener != null) {
                        mIAppPermissionListener.getPermissionFailed();
                    }
                }
                break;
        }
    }

    /**
     * 判断是否所有的权限都被授权了
     *
     * @return
     */
    private boolean isAllRequestedPermissionGranted() {
        for (PermissionEntity model : mPermissionEntities) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(mActivity, model.permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 打开应用设置界面
     *
     * @param requestCode 请求码
     * @return
     */
    private boolean openOsSettings(int requestCode) {
        try {
            Intent intent =
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + mActivity.getPackageName()));
            intent.addCategory(Intent.CATEGORY_DEFAULT);

            // Android L 之后Activity的启动模式发生了一些变化
            // 如果用了下面的 Intent.FLAG_ACTIVITY_NEW_TASK ，并且是 startActivityForResult
            // 那么会在打开新的activity的时候就会立即回调 onActivityResult
            // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mActivity.startActivityForResult(intent, requestCode);
            return true;
        } catch (Throwable e) {

        }
        return false;
    }

    /**
     * 查找申请权限的解释短语
     *
     * @param permission 权限
     * @return
     */
    private String findPermissionExplain(String permission) {
        if (mPermissionEntities != null) {
            for (PermissionEntity model : mPermissionEntities) {
                if (model != null && model.permission != null && model.permission.equals(permission)) {
                    return model.explain;
                }
            }
        }
        return null;
    }

    /**
     * 查找申请权限的名称
     *
     * @param permission 权限
     * @return
     */
    private String findPermissionName(String permission) {
        if (mPermissionEntities != null) {
            for (PermissionEntity model : mPermissionEntities) {
                if (model != null && model.permission != null && model.permission.equals(permission)) {
                    return model.name;
                }
            }
        }
        return null;
    }

    private static class PermissionEntity {
        /**
         * 权限名称
         */
        public String name;
        /**
         * 请求的权限
         */
        public String permission;
        /**
         * 解析为什么请求这个权限
         */
        public String explain;

        /**
         * 请求代码
         */
        public int requestCode;

        public PermissionEntity(String name, String permission, String explain, int requestCode) {
            this.name = name;
            this.permission = permission;
            this.explain = explain;
            this.requestCode = requestCode;
        }
    }

    /**
     * 权限申请事件监听
     */
    public interface IAppPermissionListener {

        /**
         * 申请所有权限之后的逻辑
         */
        void getPermissionSuccess();

        void getPermissionFailed();
    }


    private static String[] getManifestPermissions(Context context) {
        StringBuffer appNameAndPermissions=new StringBuffer();
        PackageManager pm = context.getPackageManager();
        List packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String packageName = context.getPackageName();
//        for (ApplicationInfo applicationInfo : packages) {
//            Log.d("test", "App: " + applicationInfo.name + " Package: " + applicationInfo.packageName);
            try {
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
                appNameAndPermissions.append(packageInfo.packageName+"*:\n");
                //Get Permissions
                String[] requestedPermissions = packageInfo.requestedPermissions;
                if(requestedPermissions != null) {
                    for (int i = 0; i < requestedPermissions.length; i++) {
                        Log.d("test", requestedPermissions[i]);
                        appNameAndPermissions.append(requestedPermissions[i]+"\n");
                    }
                    appNameAndPermissions.append("\n");
                    return requestedPermissions;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
                    Log.d(TAG, "App: Package: " + packageName+", Permissions : "+appNameAndPermissions.toString());
//    }
        return null;
    }
}
