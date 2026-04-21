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

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 备份工具类，负责将便签数据导出为可读的文本文件。
 * 采用单例模式，内部包含一个 {@link TextExport} 内部类执行具体的导出逻辑。
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils"; // 日志标签

    // 单例相关
    private static BackupUtils sInstance; // 单例实例

    /**
     * 获取 BackupUtils 的单例对象（线程安全）。
     *
     * @param context 上下文，用于初始化内部 TextExport
     * @return BackupUtils 单例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * 以下状态常量用于表示备份或恢复的状态
     */
    // SD卡未挂载
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // 备份文件不存在
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // 数据格式损坏，可能被其他程序修改
    public static final int STATE_DATA_DESTROIED               = 2;
    // 运行时异常导致备份或恢复失败
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // 备份或恢复成功
    public static final int STATE_SUCCESS                      = 4;

    private TextExport mTextExport; // 实际执行文本导出的内部类实例

    /**
     * 私有构造方法，初始化内部导出器。
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储（SD卡）是否可用（已挂载且可读写）。
     *
     * @return true 表示外部存储可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 执行导出到文本文件的操作。
     *
     * @return 导出结果状态码，如 {@link #STATE_SUCCESS} 等
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名。
     *
     * @return 文件名（不含路径）
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件所在目录路径。
     *
     * @return 目录路径字符串
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 内部类：负责将便签数据导出为格式化文本。
     */
    private static class TextExport {
        // 查询便签表所需的投影列
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,                // 便签ID
                NoteColumns.MODIFIED_DATE,     // 最后修改时间
                NoteColumns.SNIPPET,           // 内容片段（用于文件夹名等）
                NoteColumns.TYPE               // 类型（笔记/文件夹）
        };

        private static final int NOTE_COLUMN_ID = 0;
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;
        private static final int NOTE_COLUMN_SNIPPET = 2;

        // 查询数据表所需的投影列
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,      // 内容文本或位置
                DataColumns.MIME_TYPE,    // MIME类型，区分普通笔记和通话记录笔记
                DataColumns.DATA1,        // 通话记录日期
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,        // 电话号码
        };

        private static final int DATA_COLUMN_CONTENT = 0;
        private static final int DATA_COLUMN_MIME_TYPE = 1;
        private static final int DATA_COLUMN_CALL_DATE = 2;      // DATA1 列存储通话日期
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;   // DATA4 列存储电话号码

        // 文本格式模板数组，从资源文件中加载
        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME  = 0; // 文件夹名称格式索引
        private static final int FORMAT_NOTE_DATE    = 1; // 笔记日期格式索引
        private static final int FORMAT_NOTE_CONTENT = 2; // 笔记内容格式索引

        private Context mContext;
        private String mFileName;       // 导出的文件名
        private String mFileDirectory;  // 导出的文件所在目录

        public TextExport(Context context) {
            // 从资源中获取导出文本的格式化字符串
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 根据ID获取格式字符串。
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 将指定文件夹及其下的所有笔记导出到文本输出流。
         *
         * @param folderId 文件夹ID
         * @param ps       打印输出流，写入文件
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有笔记
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                            folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印笔记的最后修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // 查询该笔记的数据内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 将指定ID的笔记内容（包括通话记录等特殊类型）导出到文本输出流。
         *
         * @param noteId 笔记ID
         * @param ps     打印输出流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                            noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 通话记录类型的笔记
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // 打印通话时间
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // 打印录音文件位置（如果有）
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // 普通笔记内容
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // 在每个笔记之后打印一个分隔符（换行符 + 字母数字标记，便于阅读）
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 执行导出操作的主入口。
         *
         * @return 导出状态码
         */
        public int exportToText() {
            // 检查外部存储是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 获取指向导出文件的打印流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // 1. 导出所有文件夹（排除回收站文件夹）及其下的笔记
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称（通话记录文件夹使用资源字符串）
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // 2. 导出根目录下不属于任何文件夹的笔记（PARENT_ID = 0）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 创建并返回指向导出文件的 PrintStream。
         * 文件位于外部存储的应用特定目录下，文件名包含当前日期。
         *
         * @return PrintStream 对象，如果创建失败返回 null
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在SD卡上生成用于存储导出数据的文本文件。
     *
     * @param context           上下文
     * @param filePathResId     文件相对路径字符串资源ID
     * @param fileNameFormatResId 文件名格式字符串资源ID（包含日期占位符）
     * @return 创建的File对象，失败返回null
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        // 获取外部存储根目录
        sb.append(Environment.getExternalStorageDirectory());
        // 追加应用特定子目录
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());
        // 追加带日期的文件名
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            // 确保目录存在
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 创建新文件
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}