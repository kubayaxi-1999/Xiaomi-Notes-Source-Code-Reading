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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器，用于在系统启动完成后或时间变更时，重新注册所有未到期的提醒。
 * 通过对数据库查询所有未来提醒时间的便签，向 AlarmManager 逐一设置 Alarm，确保系统
 * 能够准时触发 {@link AlarmReceiver} 并弹出提醒。
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * 查询便签所需要的列：便签 ID 和提醒时间
     */
    private static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE
    };

    private static final int COLUMN_ID                = 0;
    private static final int COLUMN_ALERTED_DATE      = 1;

    /**
     * 接收广播后执行初始化操作。通常由如下广播触发：
     * <ul>
     *   <li>{@link Intent#ACTION_BOOT_COMPLETED} - 开机完成</li>
     *   <li>{@link Intent#ACTION_TIME_CHANGED} - 系统时间发生变化</li>
     *   <li>{@link Intent#ACTION_TIMEZONE_CHANGED} - 时区变化</li>
     * </ul>
     *
     * @param context 上下文
     * @param intent  接收到的广播 Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前时间戳，只查询提醒时间在当前时间之后的便签
        long currentDate = System.currentTimeMillis();
        Cursor c = context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[] { String.valueOf(currentDate) },
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    // 获取便签的提醒时间
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);
                    // 构建发送给 AlarmReceiver 的 Intent，携带便签 ID（通过 data URI）
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // 获取 AlarmManager 并设置一次性闹钟
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
                    // 使用 RTC_WAKEUP 类型，确保设备睡眠时也能唤醒并发出提醒
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);
                } while (c.moveToNext());
            }
            c.close();
        }
    }
}
