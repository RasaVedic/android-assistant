package com.example.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionHelper.kt
 * Handles runtime permission requests for all features PKassist needs.
 */
object PermissionHelper {

    val RUNTIME_PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.WRITE_CONTACTS)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean =
        RUNTIME_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun requestAll(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, RUNTIME_PERMISSIONS, requestCode)
    }

    fun grantedCount(context: Context): Int =
        RUNTIME_PERMISSIONS.count {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun openAppSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })
    }

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context)
        else true

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")))
        }
    }
}
