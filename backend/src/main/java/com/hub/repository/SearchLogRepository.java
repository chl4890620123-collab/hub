package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class SearchLogRepository {
    private final JdbcTemplate jdbc;
    public SearchLogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void log(long projectId,long userId,String query){jdbc.update("INSERT INTO search_log(project_id,user_id,query_text) VALUES(?,?,?)",projectId,userId,query);}
    public List<Map<String,Object>> top(long projectId){return jdbc.queryForList("SELECT query_text,COUNT(*) search_count FROM search_log WHERE project_id=? GROUP BY query_text ORDER BY search_count DESC,query_text LIMIT 3",projectId);}
}
