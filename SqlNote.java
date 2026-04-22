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

package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * SqlNote 类封装了 note 表中的一条记录（可以是文件夹或笔记），
 * 并管理其关联的 SqlData 列表（即 data 表中的详细内容）。
 * 用于 Google Tasks 同步过程中将本地便签与云端 JSON 格式相互转换，
 * 并通过 ContentProvider 提交更改到数据库。
 * 
 * 每个 SqlNote 实例对应 note 表中的一行，通过 mId 标识。
 * 它维护了当前值与待提交的差异（mDiffNoteValues），
 * 支持创建新记录和更新已有记录，并在更新时可选地验证版本号以防止冲突。
 */
public class SqlNote {
    private static final String TAG = SqlNote.class.getSimpleName();

    // 无效 ID，用于新创建的记录
    private static final int INVALID_ID = -99999;

    /**
     * 查询 note 表时使用的投影（列集合）。
     * 包含了同步模块需要的所有核心字段。
     */
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    // 各列在投影中的索引位置
    public static final int ID_COLUMN = 0;
    public static final int ALERTED_DATE_COLUMN = 1;
    public static final int BG_COLOR_ID_COLUMN = 2;
    public static final int CREATED_DATE_COLUMN = 3;
    public static final int HAS_ATTACHMENT_COLUMN = 4;
    public static final int MODIFIED_DATE_COLUMN = 5;
    public static final int NOTES_COUNT_COLUMN = 6;
    public static final int PARENT_ID_COLUMN = 7;
    public static final int SNIPPET_COLUMN = 8;
    public static final int TYPE_COLUMN = 9;
    public static final int WIDGET_ID_COLUMN = 10;
    public static final int WIDGET_TYPE_COLUMN = 11;
    public static final int SYNC_ID_COLUMN = 12;
    public static final int LOCAL_MODIFIED_COLUMN = 13;
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;
    public static final int GTASK_ID_COLUMN = 15;
    public static final int VERSION_COLUMN = 16;

    private Context mContext;               // 上下文，用于获取 ContentResolver
    private ContentResolver mContentResolver;
    private boolean mIsCreate;              // 是否为新建记录（尚未插入数据库）
    private long mId;                       // note 表主键 _id
    private long mAlertDate;                // 提醒日期
    private int mBgColorId;                 // 背景颜色资源 ID
    private long mCreatedDate;              // 创建日期（毫秒时间戳）
    private int mHasAttachment;             // 是否有附件（0/1）
    private long mModifiedDate;             // 最后修改日期
    private long mParentId;                 // 父文件夹 ID
    private String mSnippet;                // 摘要（文件夹名或笔记内容片段）
    private int mType;                      // 类型：笔记/文件夹/系统
    private int mWidgetId;                  // 桌面小部件 ID
    private int mWidgetType;                // 小部件类型（2x2/4x4）
    private long mOriginParent;             // 原始父文件夹 ID（用于从临时文件夹恢复）
    private long mVersion;                  // 版本号（用于同步冲突检测）
    private ContentValues mDiffNoteValues;  // 待提交的 note 表差异字段
    private ArrayList<SqlData> mDataList;   // 关联的 SqlData 列表（仅当类型为笔记时有效）

    // ===================== 构造方法 =====================

    /**
     * 构造一个空的 SqlNote，用于创建新记录。
     * @param context 上下文
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context);  // 默认背景色
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;                         // 默认放在根目录
        mSnippet = "";
        mType = Notes.TYPE_NOTE;               // 默认为普通笔记
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    /**
     * 从 Cursor 构造 SqlNote，用于加载已有记录。
     * @param context 上下文
     * @param c       指向 note 表当前行的 Cursor（必须包含 PROJECTION_NOTE 中的列）
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);                     // 从 Cursor 加载 note 字段
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)          // 如果是笔记，还需要加载关联的 data 数据
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 根据笔记 ID 从数据库加载 SqlNote。
     * @param context 上下文
     * @param id      note 表的 _id
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);                    // 通过 ID 查询并加载
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    // ===================== 数据加载方法 =====================

    /**
     * 根据笔记 ID 从数据库加载 note 字段。
     * @param id 笔记 ID
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] { String.valueOf(id) }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * 从当前 Cursor 位置加载 note 字段到成员变量。
     * @param c 已移动到有效位置的 Cursor
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
        // 注意：SYNC_ID, LOCAL_MODIFIED, GTASK_ID, ORIGIN_PARENT_ID 等字段在同步中也会用到，
        // 但这里未加载，实际需要在需要时单独处理（通过 mDiffNoteValues 或查询）
    }

    /**
     * 加载当前笔记关联的所有 data 记录（仅当类型为笔记时调用）。
     * 每条 data 记录被封装为 SqlData 对象，存入 mDataList。
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] { String.valueOf(mId) }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    // ===================== JSON 转换方法 =====================

    /**
     * 使用 JSON 对象设置 SqlNote 的内容（包括 note 和关联的 data）。
     * 比较新值与当前值，将有变化的字段存入 mDiffNoteValues 和各个 SqlData 的差异中。
     * 
     * @param js 包含同步信息的 JSON 对象，格式：
     *           {
     *               "note": { ... },   // note 表字段
     *               "data": [ ... ]    // data 表数组（仅对笔记有效）
     *           }
     * @return true 表示解析成功，false 表示失败
     */
    public boolean setContent(JSONObject js) {
        try {
            // 提取 note 对象
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            int type = note.getInt(NoteColumns.TYPE);
            
            // 系统文件夹不允许设置
            if (type == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder");
                return false;
            } 
            // 处理文件夹类型（仅更新 snippet 和 type）
            else if (type == Notes.TYPE_FOLDER) {
                String snippet = note.has(NoteColumns.SNIPPET) ? note.getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            } 
            // 处理笔记类型
            else if (type == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                
                // 逐一处理 note 表的各个字段
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note.getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note.getInt(NoteColumns.BG_COLOR_ID) 
                        : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                long createDate = note.has(NoteColumns.CREATED_DATE) ? note.getLong(NoteColumns.CREATED_DATE) 
                        : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note.getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note.getLong(NoteColumns.MODIFIED_DATE) 
                        : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note.getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                String snippet = note.has(NoteColumns.SNIPPET) ? note.getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID) 
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note.getInt(NoteColumns.WIDGET_TYPE) 
                        : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note.getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 处理关联的 data 列表
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    // 尝试根据 ID 找到已有的 SqlData
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                                break;
                            }
                        }
                    }
                    // 如果没有找到，创建新的 SqlData
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }
                    // 将 JSON 内容设置到 SqlData 中（内部会记录差异）
                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 将当前 SqlNote 的内容转换为 JSON 对象。
     * 注意：如果 mIsCreate 为 true（尚未插入数据库），则返回 null 并记录错误。
     * 
     * @return 表示当前笔记/文件夹的 JSONObject，若未创建则返回 null
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();
            if (mType == Notes.TYPE_NOTE) {
                // 笔记类型：输出所有字段 + data 数组
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 文件夹/系统项：只输出 ID、类型和摘要
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }

    // ===================== 辅助修改方法 =====================

    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    // ===================== Getter 方法 =====================

    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    // ===================== 提交更改到数据库 =====================

    /**
     * 将 SqlNote 的所有更改（包括 note 表和关联的 data 表）提交到数据库。
     * 
     * @param validateVersion 是否启用版本验证（用于冲突检测）
     *                        若为 true，则更新 note 表时会检查版本号；
     *                        对于 data 表，也会传递 validateVersion 和当前版本号。
     * @throws ActionFailureException 当插入失败或更新异常时抛出
     * @throws IllegalStateException 当更新时 ID 无效时抛出
     */
    public void commit(boolean validateVersion) {
        // 处理新建记录（INSERT）
        if (mIsCreate) {
            // 如果 ID 无效且差异集合中包含 ID 字段，则移除（让数据库自动生成）
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 插入 note 表
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果是笔记，还要插入所有关联的 data
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);  // 新建时不需要验证版本
                }
            }
        } 
        // 处理已有记录的更新（UPDATE）
        else {
            // 检查 ID 有效性（系统文件夹 ID 允许为 0 或负数，但普通笔记不允许）
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }

            if (mDiffNoteValues.size() > 0) {
                mVersion++;   // 每次更新前自增版本号
                int result = 0;
                if (!validateVersion) {
                    // 不验证版本：直接更新
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues,
                            "(" + NoteColumns.ID + "=?)", new String[] { String.valueOf(mId) });
                } else {
                    // 验证版本：只当当前数据库中的版本号 <= mVersion 时才更新（乐观锁）
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues,
                            "(" + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] { String.valueOf(mId), String.valueOf(mVersion) });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果是笔记，更新所有关联的 data 记录
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 提交完成后，重新从数据库加载最新数据，以保持成员变量与数据库一致
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        // 清空差异集合并标记为非新建
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}