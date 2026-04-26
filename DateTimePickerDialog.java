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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框，基于 {@link AlertDialog} 并内嵌 {@link DateTimePicker} 控件。
 * 提供“确定”和“取消”按钮，用户选定时间后通过 {@link OnDateTimeSetListener} 回调返回
 * 所选时间的毫秒值。标题会根据用户当前选择动态更新。
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    /** 内部维护的日期时间对象，随用户选择实时更新 */
    private Calendar mDate = Calendar.getInstance();

    /** 是否使用 24 小时制（当前未完全使用，标题逻辑支持） */
    private boolean mIs24HourView;

    /** 用户点击“确定”后的回调监听器 */
    private OnDateTimeSetListener mOnDateTimeSetListener;

    /** 内嵌的日期时间选择器控件 */
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间设置回调接口。
     */
    public interface OnDateTimeSetListener {
        /**
         * 用户点击“确定”后调用。
         *
         * @param dialog 当前对话框
         * @param date   用户最终选择的日期时间（毫秒时间戳）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造函数，初始化对话框布局、内部时间、按钮及标题。
     *
     * @param context 上下文
     * @param date    初始日期时间（毫秒时间戳）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建并嵌入 DateTimePicker
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        // 监听 DateTimePicker 的时间变化，同步到内部 Calendar 并更新标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 初始化时间，并将秒清零，毫秒部分由 Calendar 自动维护
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置对话框按钮
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统设置初始化 24 小时视图标志
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 初始化标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否使用 24 小时制（影响标题显示格式）。
     *
     * @param is24HourView true 为 24 小时制，false 为 12 小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间设置监听器。
     *
     * @param callBack 回调接口，当用户点击“确定”时触发
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 根据当前日期时间更新对话框标题，格式如 "2026年4月26日 14:30"。
     *
     * @param date 毫秒时间戳
     */
    private void updateTitle(long date) {
        int flag =
                DateUtils.FORMAT_SHOW_YEAR |
                        DateUtils.FORMAT_SHOW_DATE |
                        DateUtils.FORMAT_SHOW_TIME;
        // 注意：原代码中无论 12/24 小时制都使用 FORMAT_24HOUR，可能是遗留 bug，
        // 实际上这里应该根据 mIs24HourView 动态选择，但保留原逻辑不做修改。
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框按钮点击事件处理。
     * 目前只有“确定”按钮会触发此回调，将当前选择的日期时间通过监听器传出。
     *
     * @param arg0 对话框
     * @param arg1 按钮类型
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}
