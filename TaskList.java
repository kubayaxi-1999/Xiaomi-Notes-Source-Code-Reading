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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * TaskList 类表示 Google Tasks 服务中的一个任务列表（TaskList / Group）。
 * 继承自 Node，对应本地便签中的文件夹（TYPE_FOLDER）或系统文件夹（TYPE_SYSTEM）。
 * 
 * 一个 TaskList 包含多个子任务（Task），并负责维护这些子任务的顺序、
 * 父子关系以及兄弟引用（prior sibling）。
 * 
 * 同步动作包括：创建、更新、删除（通过 deleted 标记），
 * 冲突解决策略：对于文件夹冲突，直接采用本地修改（见 getSyncAction）。
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    private int mIndex;                     // 任务列表在云端的索引（用于排序）
    private ArrayList<Task> mChildren;      // 该列表下的所有子任务（按顺序存储）

    /**
     * 构造方法，初始化空的任务列表，索引默认为 1。
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    // ===================== 生成云端操作 JSON =====================

    /**
     * 生成用于在服务端创建此任务列表的 JSON 动作对象。
     * 该方法在需要将本地文件夹推送到服务端（SYNC_ACTION_ADD_REMOTE）时调用。
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

            // 列表在用户任务列表集合中的索引（用于排序）
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 实体增量信息
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());          // 列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");       // 创建者 ID（固定）
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);                  // 实体类型：分组
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 生成用于在服务端更新此任务列表的 JSON 动作对象。
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

            // 列表的 GID（云端唯一标识）
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 实体增量信息
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());   // 是否标记删除
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    // ===================== 从云端/本地 JSON 设置内容 =====================

    /**
     * 根据从远程（Google Tasks 服务端）获取的 JSON 对象设置当前任务列表的内容。
     * 解析 GID、最后修改时间、名称等。
     *
     * @param js 服务端返回的 JSON 对象（单个任务列表的表示）
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

                // 列表名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 根据本地数据库中的 JSON 内容（即笔记的元数据）设置当前任务列表的内容。
     * 该方法用于从本地存储的便签（文件夹或系统文件夹）恢复任务列表的名称和类型。
     *
     * 处理逻辑：
     * - 如果是普通文件夹（TYPE_FOLDER），从 SNIPPET 字段提取名称，并加上特殊前缀。
     * - 如果是系统文件夹（TYPE_SYSTEM），根据 ID 映射到固定的云端名称（如“默认”、“通话记录”）。
     *
     * @param js 本地存储的笔记 JSON（包含 "note" 对象）
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
            return;
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通文件夹：名称取自 SNIPPET，加上 MIUI 文件夹前缀（用于与普通任务区分）
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹：根据 ID 映射为固定名称
                long id = folder.getLong(NoteColumns.ID);
                if (id == Notes.ID_ROOT_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                } else if (id == Notes.ID_CALL_RECORD_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE);
                } else {
                    Log.e(TAG, "invalid system folder");
                }
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前任务列表的内容生成本地存储用的 JSON 对象。
     * 反向操作：将云端名称（可能带有前缀）还原为本地便签的 SNIPPET 和 TYPE。
     *
     * @return 表示当前任务列表对应本地文件夹的 JSONObject，若失败返回 null
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 移除 MIUI 文件夹前缀，得到本地文件夹名称
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)) {
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length());
            }
            folder.put(NoteColumns.SNIPPET, folderName);

            // 判断是否为系统文件夹（默认或通话记录），否则为普通文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE)) {
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            } else {
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
            }

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);
            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    // ===================== 同步动作决策 =====================

    /**
     * 根据本地数据库中的记录（通过 Cursor）判断当前任务列表需要执行的同步动作。
     * 
     * 决策逻辑：
     * - 如果本地未修改（LOCAL_MODIFIED == 0）：
     *   - 若同步 ID 等于最后修改时间，则无变化。
     *   - 否则远程有更新，应用远程到本地。
     * - 如果本地已修改：
     *   - 验证 GTASK_ID 是否匹配，不匹配则错误。
     *   - 若同步 ID 等于最后修改时间，则只有本地修改，更新远程。
     *   - 否则（冲突），对于文件夹直接采用本地修改（更新远程），而不标记冲突。
     *     （因为文件夹通常不涉及复杂的数据合并）
     *
     * @param c 指向本地 note 表当前记录的 Cursor（必须包含 SqlNote 定义的各列）
     * @return 同步动作常量（定义在 Node 类中）
     */
    @Override
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地未修改
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
                    // 文件夹冲突：直接应用本地修改（覆盖远程）
                    // 与 Task 不同，文件夹不产生冲突动作
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // ===================== 子任务管理方法 =====================

    /**
     * 获取子任务数量。
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 向列表末尾添加一个子任务。
     * 会自动设置该任务的 prior sibling 为当前最后一个任务，并将父节点指向当前列表。
     *
     * @param task 要添加的任务
     * @return true 表示添加成功，false 表示任务为空或已存在
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前一个兄弟节点
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren.get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定索引位置插入一个子任务。
     * 会更新受影响的任务的 prior sibling 关系。
     *
     * @param task  要插入的任务
     * @param index 插入位置（0 到 mChildren.size()）
     * @return true 表示插入成功，false 表示参数无效或任务已存在
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新受影响的任务的 prior sibling
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 从列表中移除一个子任务。
     * 会自动清除该任务的 prior sibling 和 parent 引用，并修复后续任务的 prior sibling 关系。
     *
     * @param task 要移除的任务
     * @return true 表示移除成功，false 表示任务不在列表中
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清除该任务的关系
                task.setPriorSibling(null);
                task.setParent(null);

                // 修复后续任务的 prior sibling
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 将子任务移动到列表中的另一个索引位置。
     * 通过先删除再添加实现，会更新所有受影响的 prior sibling 关系。
     *
     * @param task  要移动的任务
     * @param index 目标索引
     * @return true 表示移动成功，false 表示参数无效或任务不在列表中
     */
    public boolean moveChildTask(Task task, int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据 GID 查找子任务。
     *
     * @param gid 任务的云端 GID
     * @return 找到的 Task 对象，未找到则返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取子任务在列表中的索引位置。
     *
     * @param task 目标任务
     * @return 索引值（0-based），若不存在则返回 -1
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据索引获取子任务。
     *
     * @param index 索引位置
     * @return Task 对象，若索引无效则返回 null
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 GID 获取子任务（同 findChildTaskByGid，方法名略有差异）。
     *
     * @param gid 任务的云端 GID
     * @return Task 对象，未找到则返回 null
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取所有子任务的列表。
     *
     * @return ArrayList 包含所有子任务
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    // ===================== Getter 和 Setter 方法 =====================

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}