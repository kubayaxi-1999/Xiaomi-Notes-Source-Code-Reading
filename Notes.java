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

package net.micode.notes.data;

import android.net.Uri;

/**
 * 便签应用的数据契约类。
 * 定义了内容提供器（ContentProvider）的授权信息、各种URI、表结构列名、
 * 便签类型、系统文件夹ID、Intent额外参数键以及具体数据类型的常量。
 * 该类是应用与数据库之间的桥梁，其他组件通过此类定义的常量访问数据。
 */
public class Notes {
    // 内容提供器的授权字符串，用于构造URI
    public static final String AUTHORITY = "micode_notes";
    // 日志标签
    public static final String TAG = "Notes";

    // 便签项的类型：普通笔记
    public static final int TYPE_NOTE     = 0;
    // 便签项的类型：文件夹
    public static final int TYPE_FOLDER   = 1;
    // 便签项的类型：系统项（如系统文件夹）
    public static final int TYPE_SYSTEM   = 2;

    /**
     * 以下是系统文件夹的标识符
     * {@link Notes#ID_ROOT_FOLDER} 为默认根文件夹
     * {@link Notes#ID_TEMPARAY_FOLDER} 用于存放不属于任何文件夹的笔记（注意原拼写 TEMPARAY 应为 TEMPORARY）
     * {@link Notes#ID_CALL_RECORD_FOLDER} 用于存储通话记录便签
     * {@link Notes#ID_TRASH_FOLER} 为回收站文件夹（注意原拼写 TRASH_FOLER 应为 TRASH_FOLDER）
     */
    public static final int ID_ROOT_FOLDER = 0;          // 根文件夹ID
    public static final int ID_TEMPARAY_FOLDER = -1;     // 临时文件夹ID（无归属笔记）
    public static final int ID_CALL_RECORD_FOLDER = -2;  // 通话记录文件夹ID
    public static final int ID_TRASH_FOLER = -3;         // 回收站文件夹ID

    // Intent 附加数据的键，用于在不同组件间传递参数

    /** 提醒日期（毫秒时间戳） */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    /** 背景颜色ID */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    /** 桌面小部件的ID */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    /** 桌面小部件的类型 */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    /** 文件夹ID */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    /** 通话日期（用于通话记录便签） */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 桌面小部件类型常量
    public static final int TYPE_WIDGET_INVALIDE = -1;   // 无效的小部件类型（拼写 INVALIDE 应为 INVALID）
    public static final int TYPE_WIDGET_2X = 0;          // 2x2 大小的小部件
    public static final int TYPE_WIDGET_4X = 1;          // 4x4 大小的小部件

    /**
     * 便签数据项的 MIME 类型常量。
     * 内部类，定义了便签应用支持的两种数据项的 MIME 类型字符串。
     */
    public static class DataConstants {
        /** 普通文本便签的 MIME 类型，指向 {@link TextNote#CONTENT_ITEM_TYPE} */
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        /** 通话记录便签的 MIME 类型，指向 {@link CallNote#CONTENT_ITEM_TYPE} */
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * 用于查询所有便签和文件夹的 URI。
     * 格式：content://micode_notes/note
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 用于查询便签附加数据（如文本内容、通话记录详情）的 URI。
     * 格式：content://micode_notes/data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 便签表（note 表）的列名定义。
     * 该表存储文件夹和笔记的基本信息，通过 {@link #TYPE} 列区分是文件夹还是笔记。
     */
    public interface NoteColumns {
        /** 行的唯一ID，主键，类型：INTEGER (long) */
        public static final String ID = "_id";

        /** 父文件夹ID，指向同一张表中的另一行。如果为系统文件夹ID（如0，-1等），则没有父级。类型：INTEGER (long) */
        public static final String PARENT_ID = "parent_id";

        /** 创建日期，毫秒时间戳，类型：INTEGER (long) */
        public static final String CREATED_DATE = "created_date";

        /** 最后修改日期，毫秒时间戳，类型：INTEGER (long) */
        public static final String MODIFIED_DATE = "modified_date";

        /** 提醒日期，毫秒时间戳，0 表示无提醒，类型：INTEGER (long) */
        public static final String ALERTED_DATE = "alert_date";

        /** 摘要：对于文件夹是文件夹名称，对于笔记是文本内容的前缀片段，类型：TEXT */
        public static final String SNIPPET = "snippet";

        /** 桌面小部件ID，关联到添加的小部件实例，类型：INTEGER (long) */
        public static final String WIDGET_ID = "widget_id";

        /** 桌面小部件类型，参见 {@link Notes#TYPE_WIDGET_2X} 等，类型：INTEGER (long) */
        public static final String WIDGET_TYPE = "widget_type";

        /** 笔记背景颜色的资源ID，类型：INTEGER (long) */
        public static final String BG_COLOR_ID = "bg_color_id";

        /** 是否有附件（如多媒体附件），对于文本笔记为0，多媒体笔记至少为1，类型：INTEGER */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /** 文件夹中包含的笔记数量（仅对文件夹有效），类型：INTEGER (long) */
        public static final String NOTES_COUNT = "notes_count";

        /** 类型：文件夹({@link Notes#TYPE_FOLDER}) 或 笔记({@link Notes#TYPE_NOTE}) 或 系统项({@link Notes#TYPE_SYSTEM})，类型：INTEGER */
        public static final String TYPE = "type";

        /** 最后一次同步的ID，用于与云端同步，类型：INTEGER (long) */
        public static final String SYNC_ID = "sync_id";

        /** 本地修改标志，1表示已修改但未同步，0表示未修改，类型：INTEGER */
        public static final String LOCAL_MODIFIED = "local_modified";

        /** 移动到临时文件夹之前的原始父文件夹ID，用于恢复时找回原位置，类型：INTEGER */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /** Google Tasks 的任务ID，用于与 Google Tasks 同步，类型：TEXT */
        public static final String GTASK_ID = "gtask_id";

        /** 版本号，用于冲突检测，类型：INTEGER (long) */
        public static final String VERSION = "version";
    }

    /**
     * 数据表（data 表）的列名定义。
     * 该表存储便签的具体内容，每条记录通过 {@link #MIME_TYPE} 区分数据类型，
     * 并通过 {@link #NOTE_ID} 关联到 note 表中的一条笔记。
     */
    public interface DataColumns {
        /** 行的唯一ID，主键，类型：INTEGER (long) */
        public static final String ID = "_id";

        /** 该行数据的 MIME 类型，如文本便签或通话记录便签，类型：TEXT */
        public static final String MIME_TYPE = "mime_type";

        /** 关联的笔记ID，指向 note 表的 _id，类型：INTEGER (long) */
        public static final String NOTE_ID = "note_id";

        /** 创建日期，毫秒时间戳，类型：INTEGER (long) */
        public static final String CREATED_DATE = "created_date";

        /** 最后修改日期，毫秒时间戳，类型：INTEGER (long) */
        public static final String MODIFIED_DATE = "modified_date";

        /** 数据内容，具体含义取决于 MIME 类型，类型：TEXT */
        public static final String CONTENT = "content";

        /**
         * 通用数据列1，含义由 {@link #MIME_TYPE} 决定，通常存储整数型数据。
         * 例如在 TextNote 中，DATA1 表示模式（普通/清单）。
         * 类型：INTEGER
         */
        public static final String DATA1 = "data1";

        /**
         * 通用数据列2，整数型，备用。
         * 类型：INTEGER
         */
        public static final String DATA2 = "data2";

        /**
         * 通用数据列3，文本型，例如在 CallNote 中存储电话号码。
         * 类型：TEXT
         */
        public static final String DATA3 = "data3";

        /**
         * 通用数据列4，文本型，备用。
         * 类型：TEXT
         */
        public static final String DATA4 = "data4";

        /**
         * 通用数据列5，文本型，备用。
         * 类型：TEXT
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本便签的数据结构。
     * 继承自 {@link DataColumns}，添加了文本便签特有的列定义和 URI。
     */
    public static final class TextNote implements DataColumns {
        /**
         * 模式标识，存储在 DATA1 列。
         * 取值：1 表示清单模式（checkbox list），0 表示普通文本模式。
         * 类型：Integer
         */
        public static final String MODE = DATA1;

        /** 清单模式常量值 */
        public static final int MODE_CHECK_LIST = 1;

        /** 文本便签的 MIME 类型（用于多行查询） */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        /** 文本便签的单条 MIME 类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        /** 查询文本便签的专用 URI：content://micode_notes/text_note */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签的数据结构。
     * 继承自 {@link DataColumns}，定义了通话记录特有的列（通话日期、电话号码）。
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话日期，存储在 DATA1 列（整数，毫秒时间戳）。
         * 类型：INTEGER (long)
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码，存储在 DATA3 列（文本）。
         * 类型：TEXT
         */
        public static final String PHONE_NUMBER = DATA3;

        /** 通话记录便签的 MIME 类型（用于多行查询） */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        /** 通话记录便签的单条 MIME 类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        /** 查询通话记录便签的专用 URI：content://micode_notes/call_note */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}