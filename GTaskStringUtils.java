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

/**
 * GTask 同步相关的字符串常量工具类。
 * 包含了与 Google Task 服务器交互时使用的 JSON 字段名称、特殊文件夹前缀以及元数据标记。
 * 该类不可实例化，所有字段均为静态常量。
 */
public class GTaskStringUtils {

    // ==================== JSON 请求/响应字段名 ====================

    /** 操作 ID */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** 操作列表（批量操作时使用） */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** 操作类型字段 */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    /** 操作类型：创建 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 操作类型：获取全部 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 操作类型：移动 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 操作类型：更新 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    /** 创建者 ID */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** 子实体列表 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** 客户端版本 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** 是否已完成 */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** 当前列表 ID */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** 默认列表 ID */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** 是否已删除 */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** 目标列表 ID（移动操作时使用） */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** 目标父级 ID */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** 目标父级类型 */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** 实体变更增量数据 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** 实体类型 */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** 是否获取已删除项 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** 实体唯一标识 ID */
    public final static String GTASK_JSON_ID = "id";

    /** 排序索引 */
    public final static String GTASK_JSON_INDEX = "index";

    /** 最后修改时间戳（毫秒） */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** 最新的同步点标记 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /** 列表 ID */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** 列表集合 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** 名称（标题） */
    public final static String GTASK_JSON_NAME = "name";

    /** 新分配的 ID（服务器返回） */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** 笔记（任务）集合 */
    public final static String GTASK_JSON_NOTES = "notes";

    /** 父级 ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** 前一个兄弟节点 ID（用于排序） */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** 结果集 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** 源列表 ID（移动操作时使用） */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** 任务列表 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** 类型字段 */
    public final static String GTASK_JSON_TYPE = "type";

    /** 实体类型：分组（文件夹） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 实体类型：任务（便签） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /** 用户信息字段 */
    public final static String GTASK_JSON_USER = "user";

    // ==================== MIUI 便签专用标识 ====================

    /** MIUI 便签在 Google Task 中创建文件夹时添加的前缀 */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 默认文件夹名称 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 通话记录文件夹名称 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    // ==================== 元数据标记 ====================

    /** 用于存储元数据的特殊文件夹名 */
    public final static String FOLDER_META = "METADATA";

    /** 元数据头部：GTask ID */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 元数据头部：笔记信息 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 元数据头部：数据信息 */
    public final static String META_HEAD_DATA = "meta_data";

    /** 元数据笔记的标题（提醒用户不要修改或删除） */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";
}