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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 便签应用的数据库帮助类（继承 SQLiteOpenHelper）。
 * 负责创建和升级数据库（note.db），定义了两张表：note（便签/文件夹主表）和 data（详细内容表），
 * 并通过大量触发器维护数据完整性（如文件夹计数、摘要自动更新、级联删除等）。
 * 采用单例模式，避免多线程下的重复创建。
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // 数据库文件名
    private static final String DB_NAME = "note.db";
    // 数据库版本号，当前为 4
    private static final int DB_VERSION = 4;

    /**
     * 表名常量接口，便于统一引用
     */
    public interface TABLE {
        public static final String NOTE = "note";   // 便签主表（存储文件夹和笔记的基本信息）
        public static final String DATA = "data";   // 数据表（存储笔记的详细内容，如文本、通话记录等）
    }

    private static final String TAG = "NotesDatabaseHelper";
    private static NotesDatabaseHelper mInstance;   // 单例实例

    // ===================== 创建 note 表的 SQL 语句 =====================
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +                       // 主键 ID
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +         // 父文件夹 ID，指向同一张表
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +      // 提醒时间（毫秒时间戳）
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +       // 背景颜色资源 ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 创建时间，默认当前毫秒数
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +    // 是否包含附件（多媒体）
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," + // 最后修改时间
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +       // 文件夹内的笔记数量（仅对文件夹有效）
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +             // 摘要（文件夹名或笔记内容片段）
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +              // 类型：0=笔记，1=文件夹，2=系统项
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +         // 关联的桌面小部件 ID
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +      // 小部件类型（2x2 或 4x4）
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +           // 同步 ID（用于云同步）
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +    // 本地修改标志（1=已修改未同步）
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +  // 移入临时文件夹前的原始父文件夹 ID
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +            // Google Tasks 的任务 ID
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +            // 版本号（用于冲突检测）
        ")";

    // ===================== 创建 data 表的 SQL 语句 =====================
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +                       // 主键 ID
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +                      // MIME 类型（如文本便签、通话记录便签）
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +           // 关联的 note 表 ID
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +             // 文本内容（便签正文）
            DataColumns.DATA1 + " INTEGER," +                                // 通用整数列1（例如：文本便签的模式标志）
            DataColumns.DATA2 + " INTEGER," +                                // 通用整数列2
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +               // 通用文本列3（例如：通话号码）
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +               // 通用文本列4
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +                // 通用文本列5
        ")";

    // 为 data 表的 note_id 列创建索引，加速按笔记 ID 查询详细内容的操作
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ===================== 触发器（Triggers）用于维护数据一致性 =====================

    /**
     * 当更新一条笔记的 PARENT_ID（移动笔记到另一个文件夹）时，目标文件夹的 NOTES_COUNT 自动加 1。
     * 触发时机：UPDATE OF PARENT_ID 之后
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 当更新一条笔记的 PARENT_ID（从原文件夹移出）时，原文件夹的 NOTES_COUNT 自动减 1（但不会减到负数）。
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * 当插入一条新笔记时，其父文件夹的 NOTES_COUNT 自动加 1。
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * 当删除一条笔记时，其原父文件夹的 NOTES_COUNT 自动减 1（不会减到负数）。
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * 当插入一条文本类型（DataConstants.NOTE）的数据时，自动将 note 表中对应笔记的 SNIPPET 字段更新为该数据的内容。
     * 这样确保摘要字段始终与最新内容同步。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当更新一条文本类型的数据时，自动更新对应笔记的 SNIPPET。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当删除一条文本类型的数据时，将对应笔记的 SNIPPET 清空（设为空字符串）。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * 当删除一条笔记时，级联删除该笔记下所有的 data 表数据（通话记录、文本内容等）。
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 当删除一个文件夹时，级联删除该文件夹下的所有子笔记（包括子文件夹，形成递归删除）。
     * 注意：触发器在同一个表上，删除父文件夹时会自动删除所有 PARENT_ID 等于该 ID 的记录，
     * 而这些记录被删除时又会触发自身，从而实现整个子树的删除。
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 当一个文件夹被移动到回收站（PARENT_ID 变为 ID_TRASH_FOLER）时，
     * 自动将该文件夹下的所有子笔记也移动到回收站。
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    // ===================== 构造方法 =====================
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ===================== 创建 note 表及其依赖的触发器和系统文件夹 =====================
    /**
     * 创建 note 表、重新创建所有相关触发器，并插入四个系统文件夹。
     * @param db 可写的数据库实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);          // 执行建表语句
        reCreateNoteTableTriggers(db);              // 创建/重建 note 相关的触发器
        createSystemFolder(db);                     // 插入系统文件夹（通话记录、根目录、临时、回收站）
        Log.d(TAG, "note table has been created");
    }

    /**
     * 删除 note 表已有的所有触发器，然后重新创建它们。
     * 这样确保在升级数据库时触发器定义是最新的。
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 先尝试删除所有可能存在的旧触发器（避免重复创建报错）
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        // 创建新的触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * 向 note 表中插入四个必需的预定义系统文件夹：
     * 通话记录文件夹（ID_CALL_RECORD_FOLDER）、根文件夹（ID_ROOT_FOLDER）、
     * 临时文件夹（ID_TEMPARAY_FOLDER）、回收站文件夹（ID_TRASH_FOLER）。
     * 这些文件夹的类型均为 TYPE_SYSTEM。
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        // 通话记录文件夹
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 根文件夹（默认文件夹）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 临时文件夹（用于存放无归属的笔记）
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // 回收站文件夹
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    // ===================== 创建 data 表及其触发器和索引 =====================
    /**
     * 创建 data 表、重新创建 data 相关的触发器，并为 note_id 列创建索引。
     * @param db 可写的数据库实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);          // 执行建表语句
        reCreateDataTableTriggers(db);              // 创建/重建 data 相关的触发器
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);  // 创建索引
        Log.d(TAG, "data table has been created");
    }

    /**
     * 删除 data 表已有的所有触发器，然后重新创建它们。
     * 这些触发器负责在 data 表发生插入、更新、删除时自动同步 note 表的 SNIPPET 字段。
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    // ===================== 单例模式获取实例 =====================
    /**
     * 获取单例的 NotesDatabaseHelper 对象（线程安全）。
     * @param context 上下文对象
     * @return 唯一的 NotesDatabaseHelper 实例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    // ===================== SQLiteOpenHelper 回调方法 =====================
    /**
     * 首次创建数据库时调用，依次创建 note 表和 data 表。
     * @param db 数据库实例
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 数据库版本升级时调用。
     * 根据 oldVersion 依次执行升级步骤（1→2→3→4），并处理必要的触发器重建。
     * @param db         数据库实例
     * @param oldVersion 当前数据库的旧版本号
     * @param newVersion 目标新版本号
     * @throws IllegalStateException 如果升级后版本号不匹配则抛出异常
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;   // 标记是否需要重建触发器
        boolean skipV2 = false;             // 标记是否跳过了版本2的升级逻辑

        // 从版本1升级到版本2（完全重建表）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;   // 本次升级已包含从2到3的部分，跳过后续单独的2→3步骤
            oldVersion++;
        }

        // 从版本2升级到版本3（增加 GTASK_ID 列和回收站文件夹）
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;   // 需要重建触发器（因为可能删除了旧触发器）
            oldVersion++;
        }

        // 从版本3升级到版本4（增加 VERSION 列）
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // 如果之前标记了需要重建触发器，则重新创建所有触发器
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 检查是否成功升级到目标版本，否则抛出异常
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 升级到版本2：删除旧表并重新创建（相当于清空数据重建）。
     * 这是一个破坏性升级，会丢失所有已有数据。
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * 升级到版本3：
     * 1. 删除三个不再使用的修改时间触发器（update_note_modified_date_*）
     * 2. 为 note 表添加 GTASK_ID 列（用于 Google Tasks 同步）
     * 3. 插入回收站系统文件夹（ID_TRASH_FOLER）
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除未使用的触发器（之前版本可能存在的）
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加新列
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 插入回收站文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 升级到版本4：为 note 表添加 VERSION 列（用于同步时的冲突检测）。
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}