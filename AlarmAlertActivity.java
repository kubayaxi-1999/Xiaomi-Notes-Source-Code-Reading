/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 闹钟提醒弹窗 Activity，用于在便签设置的提醒时间到达时显示提醒对话框并播放铃声。
 * 界面会在锁屏状态下显示，并提供“确认”和“查看便签”两个操作选项。
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    /** 触发提醒的便签 ID */
    private long mNoteId;
    /** 便签内容摘要，用于在对话框中显示 */
    private String mSnippet;
    /** 对话框显示摘要的最大字符长度，超出部分会被截断并添加省略提示 */
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    /** 用于播放闹钟铃声的 MediaPlayer 实例 */
    MediaPlayer mPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 允许窗口在锁屏状态下显示
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭状态，则添加唤醒屏幕、保持屏幕常亮等标志
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            // 从 Intent 的 data URI 中解析便签 ID
            // URI 格式通常为 content://net.micode.notes/note/{noteId}
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            // 获取便签摘要并限制显示长度
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // 如果便签不存在或 ID 无效，直接结束 Activity
            return;
        }

        mPlayer = new MediaPlayer();
        // 检查便签是否仍然可见（未被删除或移入回收站）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog(); // 显示提醒对话框
            playAlarmSound();   // 播放闹钟铃声
        } else {
            // 便签已被删除，直接结束 Activity
            finish();
        }
    }

    /**
     * 检查屏幕当前是否处于点亮状态。
     *
     * @return true 表示屏幕已点亮，false 表示屏幕熄灭
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放系统默认闹钟铃声。
     * 会根据用户设置的静音模式是否影响闹钟流来决定音频流类型。
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟铃声 URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 读取系统设置：哪些音频流受静音模式影响
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 如果闹钟流（STREAM_ALARM）受静音模式影响，则使用系统返回的流配置
        // 否则强制使用闹钟流
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            mPlayer.setLooping(true); // 循环播放直到用户操作
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // 铃声文件不存在或格式不支持
            e.printStackTrace();
        } catch (SecurityException e) {
            // 没有读取铃声文件的权限
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // MediaPlayer 状态异常
            e.printStackTrace();
        } catch (IOException e) {
            // 设置数据源或准备时 IO 错误
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒操作对话框。
     * 包含便签摘要信息，以及“确定”按钮（关闭提醒）和“查看便签”按钮（跳转编辑）。
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);
        // 只有在屏幕亮起时才显示“查看便签”按钮，避免用户在黑暗中被强制唤醒后误操作
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击回调。
     *
     * @param dialog 被点击的对话框
     * @param which  点击的按钮类型，如 {@link DialogInterface#BUTTON_POSITIVE} 或
     *               {@link DialogInterface#BUTTON_NEGATIVE}
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 用户点击“查看便签”，跳转到便签编辑界面
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                // 其他情况（如点击“确定”），不做额外操作，交给 onDismiss 处理
                break;
        }
    }

    /**
     * 对话框消失时的回调。
     * 停止播放铃声并关闭 Activity。
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    /**
     * 停止并释放 MediaPlayer 资源。
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}