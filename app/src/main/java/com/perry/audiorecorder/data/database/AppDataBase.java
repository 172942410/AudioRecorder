package com.perry.audiorecorder.data.database;

import android.database.Cursor;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.Database;
import com.raizlabs.android.dbflow.annotation.Migration;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.QueryBuilder;
import com.raizlabs.android.dbflow.sql.migration.BaseMigration;
import com.raizlabs.android.dbflow.structure.InvalidDBConfiguration;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;

import java.lang.reflect.Field;
import java.util.List;

@Database(name = AppDataBase.NAME,version = AppDataBase.VERSION)
public class AppDataBase {
    public static final String NAME = "lianyunWuYa";//lxdPAD.db
    public static final int VERSION = 1;
    @Migration(version = AppDataBase.VERSION, database = AppDataBase.class)
    public static class DatabaseAutoUpdate extends BaseDatabaseAutoUpdate {
        @Override
        protected String getDatabaseName() {
            return AppDataBase.NAME;
        }
    }
    public static abstract class BaseDatabaseAutoUpdate extends BaseMigration {
        protected abstract String getDatabaseName();
        @Override
        public void migrate(DatabaseWrapper database) {
            try {
                List<Class<?>> classes = FlowManager.getDatabase(getDatabaseName()).getModelClasses();
                for (Class c : classes) {
                    try {
                        Cursor cursor = database.rawQuery("SELECT * FROM " + c.getSimpleName(), null);
                        Field[] fields = c.getDeclaredFields();
                        for (Field field : fields) {
                            if (field.isAnnotationPresent(Column.class)) {
                                if (cursor.getColumnIndex(field.getName()) < 0) {
                                    //缺少的字段
                                    QueryBuilder queryBuilder = new QueryBuilder().append("ALTER")
                                            .appendSpaceSeparated("TABLE")
                                            .appendSpaceSeparated(c.getSimpleName())
                                            .appendSpaceSeparated("ADD COLUMN")
                                            .appendSpaceSeparated(QueryBuilder.quoteIfNeeded(field.getName()));
                                    String sql = queryBuilder.getQuery();
                                    database.execSQL(sql);
                                }
                            }
                        }
                        cursor.close();
                    } catch (Exception e) {
                       e.printStackTrace();
                    }
                }
            } catch (InvalidDBConfiguration e) {
                e.printStackTrace();
            }
        }
    }
}
