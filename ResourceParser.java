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

package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类，负责将抽象的颜色/字体常量映射到实际的 Android 资源 ID。
 * 该类集中管理便签背景色、列表项背景、桌面小部件背景以及文本外观等资源，
 * 并提供根据用户设置获取默认值的功能。
 */
public class ResourceParser {

    // ==================== 背景色常量定义 ====================

    /** 黄色背景索引 */
    public static final int YELLOW           = 0;
    /** 蓝色背景索引 */
    public static final int BLUE             = 1;
    /** 白色背景索引 */
    public static final int WHITE            = 2;
    /** 绿色背景索引 */
    public static final int GREEN            = 3;
    /** 红色背景索引 */
    public static final int RED              = 4;

    /** 默认背景色（黄色） */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ==================== 字体大小常量定义 ====================

    /** 小号字体索引 */
    public static final int TEXT_SMALL       = 0;
    /** 中号字体索引 */
    public static final int TEXT_MEDIUM      = 1;
    /** 大号字体索引 */
    public static final int TEXT_LARGE       = 2;
    /** 超大号字体索引 */
    public static final int TEXT_SUPER       = 3;

    /** 默认字体大小（中号） */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 内部类：管理编辑界面便签背景及标题栏背景资源。
     */
    public static class NoteBgResources {
        /** 编辑界面便签内容区域背景资源数组，索引对应颜色常量 */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,
                R.drawable.edit_blue,
                R.drawable.edit_white,
                R.drawable.edit_green,
                R.drawable.edit_red
        };

        /** 编辑界面标题栏背景资源数组，索引对应颜色常量 */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,
                R.drawable.edit_title_blue,
                R.drawable.edit_title_white,
                R.drawable.edit_title_green,
                R.drawable.edit_title_red
        };

        /**
         * 根据颜色 ID 获取便签编辑区域背景资源。
         *
         * @param id 颜色索引（YELLOW, BLUE 等）
         * @return 对应的 Drawable 资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色 ID 获取编辑界面标题栏背景资源。
         *
         * @param id 颜色索引
         * @return 对应的 Drawable 资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认的背景色 ID。
     * 如果用户在设置中开启了“随机背景色”选项，则随机返回一种颜色索引；
     * 否则返回默认的黄色索引。
     *
     * @param context 上下文，用于读取 SharedPreferences
     * @return 颜色索引值（YELLOW, BLUE 等）
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机选择一个背景色
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 内部类：管理便签列表中每一项的背景资源。
     * 根据该项在列表中的位置（顶部、中间、底部、唯一项）应用不同的背景图片，
     * 以实现列表项之间的视觉分隔和圆角效果。
     */
    public static class NoteItemBgResources {
        /** 列表第一项（顶部）背景资源数组 */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,
                R.drawable.list_blue_up,
                R.drawable.list_white_up,
                R.drawable.list_green_up,
                R.drawable.list_red_up
        };

        /** 列表中间项背景资源数组 */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,
                R.drawable.list_blue_middle,
                R.drawable.list_white_middle,
                R.drawable.list_green_middle,
                R.drawable.list_red_middle
        };

        /** 列表最后一项（底部）背景资源数组 */
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,
                R.drawable.list_blue_down,
                R.drawable.list_white_down,
                R.drawable.list_green_down,
                R.drawable.list_red_down,
        };

        /** 列表中唯一一项背景资源数组 */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,
                R.drawable.list_blue_single,
                R.drawable.list_white_single,
                R.drawable.list_green_single,
                R.drawable.list_red_single
        };

        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹列表项的背景资源（固定样式，不区分颜色和位置）。
         *
         * @return 文件夹背景 Drawable 资源 ID
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    /**
     * 内部类：管理桌面小部件背景资源。
     * 支持 2x 和 4x 两种尺寸规格，每种尺寸对应多套颜色。
     */
    public static class WidgetBgResources {
        /** 2x 尺寸小部件背景资源数组 */
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,
                R.drawable.widget_2x_blue,
                R.drawable.widget_2x_white,
                R.drawable.widget_2x_green,
                R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /** 4x 尺寸小部件背景资源数组 */
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,
                R.drawable.widget_4x_blue,
                R.drawable.widget_4x_white,
                R.drawable.widget_4x_green,
                R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    /**
     * 内部类：管理文本外观（字体大小）资源。
     */
    public static class TextAppearanceResources {
        /** 文本外观样式资源数组，索引对应字体大小常量 */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,  // 小号
                R.style.TextAppearanceMedium,  // 中号
                R.style.TextAppearanceLarge,   // 大号
                R.style.TextAppearanceSuper    // 超大号
        };

        /**
         * 根据字体大小 ID 获取对应的样式资源 ID。
         * 包含一个 Hack 修复：由于 SharedPreferences 中存储的资源 ID 可能因版本变更而越界，
         * 当传入的 ID 超过数组长度时，返回默认字体大小（中号）。
         *
         * @param id 字体大小索引（TEXT_SMALL 等）
         * @return 样式资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复 SharedPreferences 中存储的资源 ID 可能越界的问题。
             * 如果 ID 大于数组长度，则返回默认字体大小。
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取可用字体样式的总数。
         *
         * @return 字体样式数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}