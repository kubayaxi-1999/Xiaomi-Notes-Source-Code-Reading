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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Task 类表示 Google Tasks 服务中的一个任务（Task）。
 * 继承自 Node，实现了与 Google Tasks API 交互所需的具体方法。
 * 
 * 一个 Task 对应本地便签中的一条笔记（TYPE_NOTE），
 * 通过 JSON 与云端同步，并维护其父子关系（TaskList 为父容器）、
 * 同级顺序（prior sibling）以及元数据（mMetaInfo）。
 * 
 * 同步动作包括：创建、更新、删除以及冲突解决。
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    private boolean mCompleted;          // 任务是否已完成（云端字段）
    private String mNotes;               // 任务的备注信息（云端 notes 字段）
    private JSONObject mMetaInfo;        // 本地便签的完整元数据（包含 note 和 data 的 JSON）
    private Task mPriorSibling;          // 同一父节点下的前一个兄弟任务（用于确定顺序）
    private TaskList mParent;            // 所属的任务列表（父容器）

    /**
     * 构造方法，初始化所有成员为默认值。
     */
    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    // ===================== 生成云端操作 JSON =====================

    /**
     * 生成用于在服务端创建此任务的 JSON 动作对象。
     * 该方法在需要将本地任务推送到服务端（SYNC_ACTION_ADD_REMOTE）时调用。
     *
     * @param actionId 动作的唯一标识，用于关联请求和响应
     * @return 包含创建动作所需字段的 JSONObject
     * @throws ActionFailureException 如果构建 JSON 失败
     */
    @Override
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 动作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 动作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务在父列表中的索引（通过父节点计算）
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 实体增量信息（entity_delta）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());          // 任务名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");       // 创建者 ID（固定）
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);                   // 实体类型：任务
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());    // 备注信息
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 父节点 ID（所属任务列表的 GID）
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // 目标父节点类型：分组（GROUP）
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // 列表 ID（与父节点相同）
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 前一个兄弟任务的 GID（用于确定任务在列表中的顺序）
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 生成用于在服务端更新此任务的 JSON 动作对象。
     * 该方法在需要将本地修改推送到服务端（SYNC_ACTION_UPDATE_REMOTE）时调用。
     *
     * @param actionId 动作的唯一标识
     * @return 包含更新动作所需字段的 JSONObject
     * @throws ActionFailureException 如果构建 JSON 失败
     */
    @Override
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 动作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 动作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务的 GID（云端唯一标识）
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体增量信息
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());   // 是否标记删除
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    // ===================== 从云端/本地 JSON 设置内容 =====================

    /**
     * 根据从远程（Google Tasks 服务端）获取的 JSON 对象设置当前任务的内容。
     * 解析 GID、最后修改时间、名称、备注、删除标志、完成状态等。
     *
     * @param js 服务端返回的 JSON 对象（单个任务的表示）
     * @throws ActionFailureException 如果解析失败
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // GID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 最后修改时间（用于冲突检测）
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 任务名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // 备注信息
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // 删除标记
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // 完成状态
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 根据本地数据库中的 JSON 内容（即 mMetaInfo）设置当前任务的内容。
     * 该方法用于从本地存储的元数据中恢复任务的名称（从 data 表的 content 字段提取）。
     * 注意：此方法仅提取任务名称，不处理其他字段。
     *
     * @param js 本地存储的完整笔记 JSON（包含 "note" 和 "data"）
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            // 只处理笔记类型（TYPE_NOTE）
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 从 data 数组中查找 MIME 类型为 NOTE 的条目，将其 content 作为任务名称
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前任务的内容生成本地存储用的 JSON 对象。
     * 如果 mMetaInfo 为 null（表示从未同步过），则根据当前任务名称构造新的 JSON；
     * 否则更新原有的 mMetaInfo 中的任务名称并返回。
     *
     * @return 表示当前任务对应本地笔记的 JSONObject，若无法生成则返回 null
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 新建任务（从云端首次同步到本地，还没有元数据）
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }

                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);          // 任务名称作为笔记的 content
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);   // 类型为笔记
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 已有元数据（之前同步过的任务）
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // 更新 data 数组中类型为 NOTE 的条目的 content
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }

                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 设置任务的元数据（从 MetaData 对象中提取）。
     * MetaData 对象存储了便签的完整 JSON 表示（包含 note 和 data）。
     *
     * @param metaData 关联的 MetaData 节点
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    // ===================== 同步动作决策 =====================

    /**
     * 根据本地数据库中的记录（通过 Cursor）判断当前任务需要执行的同步动作。
     * 该方法比较本地 note 表记录与当前 Task 对象的状态，返回对应的同步动作常量。
     *
     * 决策逻辑：
     * 1. 如果没有元数据（mMetaInfo 为空或缺少 note），则更新远程（本地有而远程无）。
     * 2. 如果元数据中没有 note ID，说明远程已删除，更新本地。
     * 3. 如果本地 note ID 与元数据中的 ID 不匹配，更新本地。
     * 4. 如果本地未修改（LOCAL_MODIFIED == 0）：
     *    - 若同步 ID 等于最后修改时间，则无变化。
     *    - 否则远程更新，应用远程到本地。
     * 5. 如果本地已修改：
     *    - 验证 GTASK_ID 是否匹配，不匹配则错误。
     *    - 若同步 ID 等于最后修改时间，则只有本地修改，更新远程。
     *    - 否则双方都有修改，产生冲突。
     *
     * @param c 指向本地 note 表当前记录的 Cursor（必须包含 SqlNote 定义的各列）
     * @return 同步动作常量（定义在 Node 类中）
     */
    @Override
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            // 从元数据中提取 note 对象
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 验证本地 note ID 与元数据中的 ID 是否一致
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 本地未修改
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方都未修改
                    return SYNC_ACTION_NONE;
                } else {
                    // 远程有更新，需要更新本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地已修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 只有本地修改，需要更新远程
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 本地和远程都有修改，产生冲突
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断当前任务是否值得保存到本地数据库。
     * 只要存在元数据，或者任务名称非空，或者备注信息非空，就值得保存。
     *
     * @return true 表示需要保存，false 表示可以忽略
     */
    @Override
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ===================== Getter 和 Setter 方法 =====================

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }
}