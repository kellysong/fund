package com.sjl.fund.app

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename AppConstant
 * @time 2026/7/22 17:48
 * @copyright(C) 2026 song
 */
object AppConstant {

    val TEMPLATE_DIR = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    ).path + "/fund/template"

    val BACKUP_DIR = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    ).path + "/fund/backups"

}