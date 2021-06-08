package com.myFramework.demo.helper;


import com.myFramework.demo.util.CollectionUtil;
import com.myFramework.demo.util.PropsUtil;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 数据库操作助手类
 */
public final class DatabaseHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseHelper.class);
    private static final ThreadLocal<Connection> CONNECTION_HOLDER;
    private static final QueryRunner QUERY_RUNNER;
    private static final BasicDataSource DATA_SOURCE;

    private static final String DRIVER;
    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        CONNECTION_HOLDER = new ThreadLocal<Connection>();
        QUERY_RUNNER = new QueryRunner();


        Properties properties = PropsUtil.loadProps("config.properties");
        DRIVER = properties.getProperty("jdbc.driver");
        URL = properties.getProperty("jdbc.url");
        USERNAME = properties.getProperty("jdbc.username");
        PASSWORD = properties.getProperty("jdbc.password");

        DATA_SOURCE = new BasicDataSource();
        DATA_SOURCE.setDriverClassName(DRIVER);
        DATA_SOURCE.setUrl(URL);
        DATA_SOURCE.setUsername(USERNAME);
        DATA_SOURCE.setPassword(PASSWORD);
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() {
        Connection conn = CONNECTION_HOLDER.get();
        if (conn == null) {
            try {
                conn = DATA_SOURCE.getConnection();
            } catch (SQLException e) {
                LOGGER.error("get connection failure", e);
                throw new RuntimeException(e);
            } finally {
                CONNECTION_HOLDER.set(conn);
            }
        }

        return conn;
    }


    /**
     * 查询实体列表
     */
    public static <T> List<T> queryEntityList(Class<T> entityClass, String sql, Object... params) {
        List<T> entityList;
        try {
            Connection conn = getConnection();
            entityList = QUERY_RUNNER.query(conn, sql, new BeanListHandler<T>(entityClass), params);
        } catch (SQLException e) {
            LOGGER.error("query entity list failure", e);
            throw new RuntimeException(e);
        }
        return entityList;
    }

    /**
     * 查询实体
     */
    public static <T> T queryEntity(Class<T> entityClass, String sql, Object... params) {
        T entity;
        try {
            Connection conn = getConnection();
            entity = QUERY_RUNNER.query(conn, sql, new BeanHandler<T>(entityClass), params);
        } catch (SQLException e) {
            LOGGER.error("query entity failure", e);
            throw new RuntimeException(e);
        }
        return entity;
    }

    /**
     * 执行查询语句
     */
    public static List<Map<String, Object>> executeQuery(String sql, Object... params) {
        List<Map<String, Object>> result;
        try {
            Connection conn = getConnection();
            result = QUERY_RUNNER.query(conn, sql, new MapListHandler(), params);
        } catch (SQLException e) {
            LOGGER.error("execute query failure", e);
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * 执行更新语句（包括update,insert,delete）
     */
    public static int executeUpdate(String sql, Object... params) {
        int rows = 0;
        try {
            Connection conn = getConnection();
            rows = QUERY_RUNNER.update(conn, sql, params);
        } catch (SQLException e) {
            LOGGER.error("execute update failure", e);
            throw new RuntimeException(e);
        }
        return rows;
    }

    /**
     * 插入实体
     */
    public static <T> boolean insertEntity(Class<T> entityClass, Map<String, Object> fieldMap) {
        if (CollectionUtil.isEmpty(fieldMap)) {
            LOGGER.error("can not insert entity:fieldMap is empty");
            return false;
        }

        String sql = "INSERT INTO " + getTableName(entityClass);
        StringBuilder cloumns = new StringBuilder("(");
        StringBuilder values = new StringBuilder("(");
        for (String fieldName : fieldMap.keySet()) {
            cloumns.append(fieldName).append(", ");
            values.append("?, ");
        }
        cloumns.replace(cloumns.lastIndexOf(", "), cloumns.length(), ")");
        values.replace(values.lastIndexOf(","), values.length(), ")");
        sql += cloumns + " VALUES " + values;
        LOGGER.info("insert entity:" + sql.toString());
        Object[] params = fieldMap.values().toArray();
        return executeUpdate(sql, params) == 1;
    }

    /**
     * 更新实体
     */

    public static <T> boolean updateEntity(Class<T> entityClass, long id, Map<String, Object> fieldMap) {
        if (CollectionUtil.isEmpty(fieldMap)) {
            LOGGER.error("can not update entity:fieldMap is empty");
            return false;
        }
        String sql = " UPDATE " + getTableName(entityClass) + " SET ";
        StringBuilder cloumns = new StringBuilder();
        for (String fieldName : fieldMap.keySet()) {
            cloumns.append(fieldName).append("=?, ");
        }
        sql += cloumns.substring(0, cloumns.lastIndexOf(", ")) + " WHERE id = ?";
        List<Object> paramsList = new ArrayList<Object>();
        paramsList.addAll(fieldMap.values());
        paramsList.add(id);
        Object[] params = paramsList.toArray();

        return executeUpdate(sql, params) == 1;
    }

    /**
     * 删除实体
     */
    public static <T> boolean deleteEntity(Class<T> entitClass, long id) {
        String sql = "DELETE FROM " + getTableName(entitClass) + " WHERE id = ?";
        return executeUpdate(sql, id) == 1;
    }

    private static String getTableName(Class<?> entityClass) {
        return entityClass.getSimpleName().toLowerCase();
    }

    /**
     * 执行sql文件
     */
    public static void executeSqlFile(String filePath) {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuffer sb = new StringBuffer();
        try {
            String sql;
            while ((sql = reader.readLine()) != null) {
                sb.append(sql);
            }
            String[] split = sb.toString().split(";");
            for(String str :split){
                DatabaseHelper.executeUpdate(str);
            }
        } catch (Exception e) {
            LOGGER.error("execute sql file failure", e);
            throw new RuntimeException(e);
        }
    }

}
