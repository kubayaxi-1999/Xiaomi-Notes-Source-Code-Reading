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
import org.json.JSONObject;

/**
 * 同步节点抽象基类。
 * 表示一个可以与 Google Tasks 服务端同步的数据单元（例如一个任务或元数据）。
 * 定义了同步操作的类型常量、通用属性（GID、名称、修改时间、删除标记）以及
 * 子类必须实现的序列化/反序列化和同步决策方法。
 */
public abstract class Node {
    // ===================== 同步动作类型常量 =====================
    
    /** 无同步操作，本地与服务端数据一致 */
    public static final int SYNC_ACTION_NONE = 0;

    /** 需要在服务端新增此节点（本地有，远程无） */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 需要在本地新增此节点（远程有，本地无） */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 需要删除服务端的此节点（本地已删除，远程仍存在） */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 需要删除本地的此节点（远程已删除，本地仍存在） */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 需要更新服务端的此节点（本地修改较新） */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 需要更新本地的此节点（远程修改较新） */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 更新冲突，需要人工解决或特殊处理 */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 同步过程中发生错误 */
    public static final int SYNC_ACTION_ERROR = 8;

    // ===================== 成员变量 =====================
    
    /** Google Tasks 服务端分配的唯一标识符（Global Task ID） */
    private String mGid;

    /** 节点的名称（对应任务的标题或元数据的固定名称） */
    private String mName;

    /** 最后修改时间（毫秒时间戳，用于判断冲突） */
    private long mLastModified;

    /** 是否已被标记为删除（软删除标记） */
    private boolean mDeleted;

    /**
     * 构造方法，初始化所有属性为默认值。
     */
    public Node() {
        mGid = null;           // 新节点尚未获得服务端 GID
        mName = "";            // 默认名称为空字符串
        mLastModified = 0;     // 修改时间为 0
        mDeleted = false;      // 未删除
    }

    // ===================== 抽象方法（由子类实现具体逻辑） =====================

    /**
     * 生成用于在服务端创建此节点的 JSON 动作对象。
     * 该方法在需要将本地节点同步到服务端（SYNC_ACTION_ADD_REMOTE）时调用。
     *
     * @param actionId 动作的唯一标识，用于关联请求和响应
     * @return 包含创建动作所需字段的 JSONObject
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成用于在服务端更新此节点的 JSON 动作对象。
     * 该方法在需要将本地修改推送到服务端（SYNC_ACTION_UPDATE_REMOTE）时调用。
     *
     * @param actionId 动作的唯一标识
     * @return 包含更新动作所需字段的 JSONObject
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 根据从服务端获取的 JSON 数据设置当前节点的内容。
     * 用于将远程数据反序列化为本地节点对象。
     *
     * @param js 服务端返回的 JSON 对象，包含节点的完整信息
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 根据本地数据库中的 JSON 数据设置当前节点的内容。
     * 用于将本地存储的 JSON 格式数据反序列化为节点对象（通常是从数据库读取）。
     *
     * @param js 本地存储的 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前节点的内容生成为本地存储用的 JSON 对象。
     * 用于将节点对象序列化为 JSON 格式以便存入本地数据库。
     *
     * @return 表示当前节点内容的 JSONObject
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据本地数据库游标判断当前节点与本地记录的同步动作。
     * 该方法用于比较本地数据库中的节点信息和当前节点状态，
     * 确定需要执行的同步操作（新增、更新、删除、冲突等）。
     *
     * @param c 指向本地数据库记录的 Cursor（通常包含 _id, gid, last_modified, deleted 等列）
     * @return 同步动作类型常量，如 SYNC_ACTION_ADD_REMOTE, SYNC_ACTION_UPDATE_LOCAL 等
     */
    public abstract int getSyncAction(Cursor c);

    // ===================== Getter 和 Setter 方法 =====================

    /**
     * 设置服务端 GID。
     * @param gid Google Tasks 全局唯一标识
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 设置节点名称。
     * @param name 节点名称（任务标题）
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * 设置最后修改时间。
     * @param lastModified 毫秒时间戳
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 设置删除标记。
     * @param deleted true 表示已删除（软删除），false 表示未删除
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取服务端 GID。
     * @return GID 字符串，可能为 null（尚未同步的本地节点）
     */
    public String getGid() {
        return this.mGid;
    }

    /**
     * 获取节点名称。
     * @return 名称字符串
     */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取最后修改时间。
     * @return 毫秒时间戳
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取删除标记状态。
     * @return true 表示已删除，false 表示未删除
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }
}