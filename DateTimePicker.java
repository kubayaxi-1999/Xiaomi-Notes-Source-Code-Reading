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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 自定义日期时间选择器，基于 {@link FrameLayout} 布局，内含四个 NumberPicker
 * 分别用于选择“星期几（日期）”、“小时”、“分钟”和“上午/下午”。
 * 支持 12/24 小时制切换，并能联动更新日期（如小时滚动至 23→0 时自动跳转下一天）。
 */
public class DateTimePicker extends FrameLayout {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLE_STATE = true;

    /** 12 小时制的半天小时数 */
    private static final int HOURS_IN_HALF_DAY = 12;
    /** 24 小时制的全天小时数 */
    private static final int HOURS_IN_ALL_DAY = 24;
    /** 一周天数（日期选择器显示的天数范围） */
    private static final int DAYS_IN_ALL_WEEK = 7;

    /** 日期选择器最小值（0） */
    private static final int DATE_SPINNER_MIN_VAL = 0;
    /** 日期选择器最大值（6） */
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;

    /** 24 小时制下小时选择器最小值 (0) */
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;
    /** 24 小时制下小时选择器最大值 (23) */
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;

    /** 12 小时制下小时选择器最小值 (1) */
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;
    /** 12 小时制下小时选择器最大值 (12) */
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;

    /** 分钟选择器最小值 (0) */
    private static final int MINUT_SPINNER_MIN_VAL = 0;
    /** 分钟选择器最大值 (59) */
    private static final int MINUT_SPINNER_MAX_VAL = 59;

    /** AM/PM 选择器最小值 (0 表示 AM) */
    private static final int AMPM_SPINNER_MIN_VAL = 0;
    /** AM/PM 选择器最大值 (1 表示 PM) */
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    // ==================== UI 控件 ====================

    private final NumberPicker mDateSpinner;   // 星期/日期选择器
    private final NumberPicker mHourSpinner;   // 小时选择器
    private final NumberPicker mMinuteSpinner; // 分钟选择器
    private final NumberPicker mAmPmSpinner;   // 上午/下午选择器

    /** 当前内部维护的日期时间 */
    private Calendar mDate;

    /** 日期选择器显示的文本数组（7 天带星期） */
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    /** 当前是否为上午 */
    private boolean mIsAm;

    /** 是否使用 24 小时制 */
    private boolean mIs24HourView;

    /** 组件是否可用 */
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    /** 是否处于初始化状态（防止初始化时触发回调） */
    private boolean mInitialising;

    /** 日期时间变更监听器 */
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    // ==================== 监听器 ====================

    /**
     * 日期选择器变化监听器。
     * 当用户滚动日期时，根据新旧值的差值调整 {@link #mDate} 的天数，
     * 并刷新日期显示与通知外部监听器。
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();  // 重新生成显示的 7 天范围
            onDateTimeChanged();
        }
    };

    /**
     * 小时选择器变化监听器。
     * 处理 12/24 小时制下的变化逻辑，包括：
     * <ul>
     *   <li>在 12 小时制下，当小时从 11 变为 12 或从 12 变为 11 时切换 AM/PM</li>
     *   <li>在 24 小时制下，当小时从 23 变为 0 或从 0 变为 23 时自动调整日期</li>
     *   <li>更新内部 {@link #mDate} 的小时字段，并通知外部</li>
     * </ul>
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance(); // 用于临时计算日期变化

            if (!mIs24HourView) {
                // 12 小时制：检测是否跨越了上午/下午边界
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    // 下午 11→12：实际时间已进入第二天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    // 上午 12→11：实际时间倒回前一天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
                // 在 11 和 12 之间切换时翻转 AM/PM 标志
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                }
            } else {
                // 24 小时制：处理 23→0（跨天）和 0→23（倒退一天）
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 计算实际 24 小时制的小时数
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();

            // 如果日期发生了变化，同步更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟选择器变化监听器。
     * 处理分钟的循环滚动（59→0 增加一小时，0→59 减少一小时），
     * 并同步更新小时和 AM/PM 状态。
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;

            // 判断是否发生了越界循环
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1; // 增加一小时
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1; // 减少一小时
            }

            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl(); // 小时变化可能引起日期变化，刷新日期显示
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                } else {
                    mIsAm = true;
                }
                updateAmPmControl();
            }
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * AM/PM 选择器变化监听器。
     * 切换上午/下午时，内部小时字段增加或减少 12 小时，并更新显示。
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    /**
     * 日期时间变更回调接口，当任何时间字段发生变化时通知外部。
     */
    public interface OnDateTimeChangedListener {
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    // ==================== 构造函数 ====================

    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 主构造函数，初始化各 NumberPicker、设置范围、绑定监听器，并加载当前日期。
     *
     * @param context      上下文
     * @param date         初始日期时间（毫秒时间戳）
     * @param is24HourView 是否使用 24 小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;
        inflate(context, R.layout.datetime_picker, this);

        // 日期选择器
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 小时选择器
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // 分钟选择器（启用手势长按快速滚动）
        mMinuteSpinner = (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // AM/PM 选择器
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 初始化控件显示范围及当前值
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        set24HourView(is24HourView);

        setCurrentDate(date);
        setEnabled(isEnabled());

        mInitialising = false;
    }

    // ==================== 公共方法 ====================

    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前选中的日期时间（毫秒时间戳）。
     *
     * @return 日期时间的毫秒值
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前日期时间。
     *
     * @param date 毫秒时间戳
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前日期时间（各字段单独指定）。
     */
    public void setCurrentDate(int year, int month,
                               int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取 24 小时制的小时（0-23）。
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前小时选择器应该显示的数值。
     * 24 小时制下直接返回 0-23，12 小时制下返回 1-12（0 点显示为 12）。
     */
    private int getCurrentHour() {
        if (mIs24HourView) {
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        if (!mIs24HourView) {
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /**
     * @return true 表示当前使用 24 小时制
     */
    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * 设置 24 小时制或 12 小时制（AM/PM）。
     *
     * @param is24HourView true 为 24 小时制，false 为 12 小时制
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        int hour = getCurrentHourOfDay();
        updateHourControl();
        setCurrentHour(hour); // 保持实际时间不变，只改变显示
        updateAmPmControl();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 更新日期选择器显示，显示当前日期前后各 3 天共 7 天的日期和星期。
     * 使用格式如 "MM.dd EEEE"（例如 "04.28 星期一"）。
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);
        mDateSpinner.setDisplayedValues(null);
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2); // 中间项为当天
        mDateSpinner.invalidate();
    }

    /**
     * 更新上午/下午选择器的可见性和选中状态。
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 根据 12/24 小时制更新小时选择器的取值范围。
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置日期时间变更监听器。
     *
     * @param callback 回调，若为 null 则不触发任何操作
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 通知外部监听器日期时间已变更。
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}
