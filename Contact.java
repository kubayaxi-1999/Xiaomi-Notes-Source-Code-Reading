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

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类。
 * 提供根据电话号码查询联系人姓名的功能，并使用缓存提高性能。
 * 注意：本类中使用的查询语句依赖于旧版 Android 联系人数据库结构（如 phone_lookup 表），
 * 在新版系统中可能已不适用，仅供学习参考。
 */
public class Contact {
    // 静态缓存：键为电话号码，值为对应的联系人姓名
    private static HashMap<String, String> sContactCache;
    // 日志标签，用于调试输出
    private static final String TAG = "Contact";

    /**
     * 查询联系人的 SQL selection 语句模板。
     * 使用 PHONE_NUMBERS_EQUAL 函数实现号码匹配（忽略格式差异）。
     * 要求数据行的 MIME 类型为 Phone.CONTENT_ITEM_TYPE（即属于电话号码类型），
     * 并且该原始联系人 ID 必须存在于 phone_lookup 表中，
     * 其中 min_match 字段需要与经过处理的号码匹配（占位符 '+' 稍后会被替换）。
     *
     * 注意：phone_lookup 是 ContactsContract 内部使用的优化表，并非公开 API，
     * 此处用法依赖系统实现细节，存在兼容性风险。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名。
     *
     * @param context      应用上下文，用于访问 ContentResolver
     * @param phoneNumber  要查询的电话号码（字符串形式，可包含分隔符）
     * @return             匹配到的联系人姓名，如果未找到或出错则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 懒加载：首次调用时创建缓存实例
        if (sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 先检查缓存中是否已有该号码对应的姓名，若有则直接返回
        if (sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 使用 PhoneNumberUtils.toCallerIDMinMatch(phoneNumber) 生成电话号码的“最小匹配键”
        // 该键是用于高效匹配的规范化形式（例如只保留数字，并取后几位）。
        // 将模板 CALLER_ID_SELECTION 中的 '+' 占位符替换为实际的最小匹配键。
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询 Data.CONTENT_URI（数据表），只获取 Phone.DISPLAY_NAME 列
        // 查询条件使用替换后的 selection，参数为原始电话号码（用于 PHONE_NUMBERS_EQUAL 比较）
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String[]{Phone.DISPLAY_NAME},
                selection,
                new String[]{phoneNumber},
                null);  // 不需要排序

        // 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 从第一行第一列（索引0）取出联系人姓名
                String name = cursor.getString(0);
                // 将结果存入缓存，避免下次重复查询
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 防御性编程：理论上 cursor.getString(0) 不会越界，
                // 但如果结果集结构异常（例如查询返回的列数少于1），则捕获异常并记录
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 确保游标被关闭，释放数据库资源
                cursor.close();
            }
        } else {
            // 未查询到任何匹配记录
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}