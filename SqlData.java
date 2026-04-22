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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SqlData 类封装了 data 表中的一条记录，用于在 Google Tasks 同步过程中
 * 将便签的详细内容（文本、通话记录等）与 JSON 格式相互转换，
 * 并通过 ContentProvider 提交更改到数据库。
 * 
 * 每个 SqlData 实例对应 data 表中的一行，通过 mDataId 标识。
 * 它维护了当前值与待提交的差异（mDiffDataValues），
 * 支持创建新记录和更新已有记录，并在更新时可选地验证笔记版本号以防止冲突。
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    // 表示无效的数据 ID（-99999），用于新创建的记录
    private static final int INVALID_ID = -99999;

    /**
     * 查询 data 表时使用的投影（列集合）。
     * 包含：ID, MIME_TYPE, CONTENT, DATA1, DATA3。
     * 这些是同步模块需要使用的核心字段。
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 各列在投影中的索引位置
    public static final int DATA_ID_COLUMN = 0;               // _id
    public static final int DATA_MIME_TYPE_COLUMN = 1;       // mime_type
    public static final int DATA_CONTENT_COLUMN = 2;         // content
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;  // data1
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;  // data3

    private ContentResolver mContentResolver;   // 内容解析器，用于操作 ContentProvider
    private boolean mIsCreate;                  // 是否为新建记录（尚未插入数据库）
    private long mDataId;                       // 数据记录的 ID（对应 data 表 _id）
    private String mDataMimeType;               // MIME 类型（如文本便签或通话记录）
    private String mDataContent;                // 内容文本
    private long mDataContentData1;             // 通用整数数据（例如清单模式标志）
    private String mDataContentData3;           // 通用文本数据（例如电话号码）
    private ContentValues mDiffDataValues;      // 待提交的差异字段集合（仅包含变化的部分）

    /**
     * 构造一个空的 SqlData，用于创建新记录。
     * @param context 上下文，用于获取 ContentResolver
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;                      // 标记为新建
        mDataId = INVALID_ID;                  // 初始 ID 无效
        mDataMimeType = DataConstants.NOTE;    // 默认 MIME 类型为普通文本便签
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues(); // 差异集合为空
    }

    /**
     * 从数据库游标构造 SqlData，用于加载已有记录。
     * @param context 上下文
     * @param c       指向 data 表当前行的 Cursor（必须包含 PROJECTION_DATA 中的列）
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;                     // 从已有数据加载，不是新建
        loadFromCursor(c);                     // 从 Cursor 填充成员变量
        mDiffDataValues = new ContentValues(); // 差异集合初始为空
    }

    /**
     * 从 Cursor 中加载当前行的数据到成员变量。
     * @param c 已经移动到有效位置的 Cursor
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 使用 JSON 对象设置 SqlData 的内容。
     * 比较新值与当前值，将有变化的字段存入 mDiffDataValues 中，
     * 同时更新成员变量。
     * 
     * @param js 包含 data 表字段的 JSON 对象（字段名与 DataColumns 常量一致）
     * @throws JSONException 如果 JSON 解析失败
     */
    public void setContent(JSONObject js) throws JSONException {
        // 处理 ID 字段（如果 JSON 中有 ID，则使用，否则保持 INVALID_ID）
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 处理 MIME_TYPE
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 处理 CONTENT
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 处理 DATA1
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理 DATA3
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前 SqlData 的内容转换为 JSON 对象。
     * 注意：如果 mIsCreate 为 true（尚未插入数据库），则返回 null 并记录错误。
     * 
     * @return 表示当前数据的 JSONObject，若未创建则返回 null
     * @throws JSONException 如果构建 JSON 失败
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将 SqlData 的更改提交到数据库（插入或更新）。
     * 
     * @param noteId         关联的笔记 ID（note 表的 _id）
     * @param validateVersion 是否启用版本验证（用于冲突检测）
     * @param version        期望的当前笔记版本号（仅在 validateVersion 为 true 时使用）
     * @throws ActionFailureException 当插入失败或更新影响行数为 0 且需要验证时抛出
     */
    public void commit(long noteId, boolean validateVersion, long version) {
        // 处理新建记录（INSERT）
        if (mIsCreate) {
            // 如果当前 mDataId 无效且差异集合中包含 ID 字段，则移除它（让数据库自动生成）
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 设置关联的笔记 ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            // 通过 ContentProvider 插入新记录
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从返回的 URI 中解析出新记录的 ID（格式：content://.../data/123）
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 处理已有记录的更新（UPDATE）
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不验证版本：直接更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 验证版本：只在该笔记的 VERSION 字段与给定的 version 匹配时才更新
                    // 子查询：从 note 表中检查指定 noteId 的版本号是否等于 version
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    // 更新行数为 0，可能由于版本不匹配或没有实际变化
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 提交完成后清空差异集合，并将标记改为非新建
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取数据记录的 ID（对应 data 表 _id）。
     * @return 数据 ID，若尚未插入则返回 INVALID_ID
     */
    public long getId() {
        return mDataId;
    }
}