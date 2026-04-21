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

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 便签应用的内容提供器（ContentProvider）。
 * 负责对外提供数据访问接口，支持对 note 表和 data 表的 CRUD 操作，并支持系统搜索建议。
 * URI 格式：
 * - content://micode_notes/note        → 操作 note 表（所有笔记/文件夹）
 * - content://micode_notes/note/#      → 操作 note 表中指定 ID 的行
 * - content://micode_notes/data        → 操作 data 表（所有详细内容）
 * - content://micode_notes/data/#      → 操作 data 表中指定 ID 的行
 * - content://micode_notes/search      → 搜索笔记（需带 pattern 参数）
 * - content://micode_notes/search_suggest_query → 搜索建议（供系统快速搜索框使用）
 */
public class NotesProvider extends ContentProvider {
    // URI 匹配器，用于将传入的 URI 映射到对应的操作码
    private static final UriMatcher mMatcher;
    // 数据库帮助类实例
    private NotesDatabaseHelper mHelper;
    private static final String TAG = "NotesProvider";

    // URI 匹配码：操作整个 note 表
    private static final int URI_NOTE            = 1;
    // URI 匹配码：操作 note 表中的单条记录
    private static final int URI_NOTE_ITEM       = 2;
    // URI 匹配码：操作整个 data 表
    private static final int URI_DATA            = 3;
    // URI 匹配码：操作 data 表中的单条记录
    private static final int URI_DATA_ITEM       = 4;
    // URI 匹配码：搜索笔记（自定义搜索）
    private static final int URI_SEARCH          = 5;
    // URI 匹配码：系统搜索建议（供 SearchManager 使用）
    private static final int URI_SEARCH_SUGGEST  = 6;

    // 静态初始化块，注册所有支持的 URI 及其匹配码
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        // SearchManager 要求的搜索建议路径
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * 搜索结果的投影（projection）定义。
     * x'0A' 是 SQLite 中的换行符（\n），此处使用 TRIM(REPLACE(...)) 去除换行和首尾空格，
     * 以便在搜索结果中显示更多内容。
     * 字段映射到 SearchManager 要求的建议列：
     * - SUGGEST_COLUMN_INTENT_EXTRA_DATA: 笔记 ID
     * - SUGGEST_COLUMN_TEXT_1: 第一行文本（笔记摘要）
     * - SUGGEST_COLUMN_TEXT_2: 第二行文本（同样使用摘要，可自定义）
     * - SUGGEST_COLUMN_ICON_1: 图标资源 ID
     * - SUGGEST_COLUMN_INTENT_ACTION: 点击后触发的 Intent Action（VIEW）
     * - SUGGEST_COLUMN_INTENT_DATA: Intent 携带的数据 MIME 类型
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 搜索查询的 SQL 语句模板。
     * 从 note 表中查询符合条件的笔记：
     * - 笔记摘要（snippet）模糊匹配用户输入的关键字
     * - 父文件夹不是回收站（ID_TRASH_FOLER）
     * - 类型必须是普通笔记（TYPE_NOTE），不包括文件夹
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * 在 ContentProvider 创建时调用，初始化数据库帮助类实例。
     * @return true 表示初始化成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据。
     * @param uri 查询的 URI，决定操作哪张表或哪个具体记录
     * @param projection 需要返回的列名数组
     * @param selection 查询条件（WHERE 子句）
     * @param selectionArgs 查询条件中的占位符值
     * @param sortOrder 排序规则
     * @return 查询结果的 Cursor，已设置通知 URI
     * @throws IllegalArgumentException 如果 URI 未知或搜索查询的参数不合法
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 查询整个 note 表
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_NOTE_ITEM:
                // 查询 note 表中指定 ID 的行，URI 格式：note/123
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                // 查询整个 data 表
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                // 查询 data 表中指定 ID 的行
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索功能：不允许外部传入 sortOrder、projection 等参数，因为搜索使用固定 SQL
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议 URI 示例：content://micode_notes/search_suggest_query/关键词
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 自定义搜索 URI 示例：content://micode_notes/search?pattern=关键词
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 将关键词包装成 SQL LIKE 模式：%关键词%
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 如果查询成功，设置 Cursor 的通知 URI，当数据变化时能通知观察者
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据。
     * @param uri 插入的目标 URI（必须是 URI_NOTE 或 URI_DATA）
     * @param values 要插入的键值对
     * @return 新插入记录的 URI（包含自动生成的 ID）
     * @throws IllegalArgumentException 如果 URI 类型不支持插入
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 向 note 表插入一条记录
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                // 向 data 表插入一条记录，必须包含 NOTE_ID 字段
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 如果插入的是 note 记录，通知 note URI 发生变化
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        // 如果插入的是 data 记录，通知 data URI 发生变化
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }
        // 返回新记录的完整 URI
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据。
     * @param uri 要删除的 URI（支持表级和单条记录）
     * @param selection 删除条件（WHERE 子句）
     * @param selectionArgs 条件中的占位符值
     * @return 删除的行数
     * @throws IllegalArgumentException 如果 URI 未知
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;  // 标记是否删除了 data 表的数据
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除 note 表中的多条记录，但限制 ID > 0（系统文件夹 ID <= 0 不允许删除）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                // 系统文件夹（ID <= 0）不允许删除
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 如果删除了任何数据，通知相应的 URI 发生变化
        if (count > 0) {
            if (deleteData) {
                // 删除 data 数据时，关联的 note 表也可能受影响（例如摘要变空），因此通知 note URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据。
     * @param uri 要更新的 URI
     * @param values 要更新的键值对
     * @param selection 更新条件（WHERE 子句）
     * @param selectionArgs 条件中的占位符值
     * @return 更新的行数
     * @throws IllegalArgumentException 如果 URI 未知
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;  // 标记是否更新了 data 表的数据
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新 note 表多条记录前，先增加版本号（用于同步冲突检测）
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            if (updateData) {
                // 更新 data 数据时，关联的 note 表内容可能变化（如摘要更新），通知 note URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 辅助方法：将外部传入的 selection 条件包装成 “ AND (selection)” 形式。
     * @param selection 原始条件字符串，可能为空
     * @return 如果 selection 不为空，返回 " AND (selection)"，否则返回空字符串
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记的版本号（VERSION 字段）。
     * 用于同步时检测冲突：每次更新 note 表记录时，版本号自动 +1。
     * @param id 指定笔记的 ID，若为 -1 则根据 selection 批量增加
     * @param selection 更新条件（WHERE 子句）
     * @param selectionArgs 条件中的占位符值
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 手动替换占位符 ? 为实际参数（注意：这种方式存在 SQL 注入风险，但此处 selectionArgs 来自内部调用，相对安全）
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 返回给定 URI 对应的 MIME 类型。
     * 本应用中未实现，实际使用时需根据业务补充（例如返回 vnd.android.cursor.dir/item 类型）。
     * @param uri 查询的 URI
     * @return MIME 类型字符串，当前返回 null
     */
    @Override
    public String getType(Uri uri) {
        // TODO: 根据 URI 返回正确的 MIME 类型，例如：
        // if (mMatcher.match(uri) == URI_NOTE) return "vnd.android.cursor.dir/vnd.micode.note";
        // if (mMatcher.match(uri) == URI_NOTE_ITEM) return "vnd.android.cursor.item/vnd.micode.note";
        return null;
    }
}