package com.hub.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class SearchLogRepository {
    private final JdbcTemplate jdbc;
    public SearchLogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void log(long projectId,long userId,String query){jdbc.update("INSERT INTO search_log(project_id,user_id,query_text) VALUES(?,?,?)",projectId,userId,query);}
    public List<Map<String,Object>> top(long projectId){return jdbc.queryForList("SELECT query_text,COUNT(*) search_count FROM search_log WHERE project_id=? GROUP BY query_text ORDER BY search_count DESC,query_text LIMIT 3",projectId);}

    /** Retention cleanup only: search_log is a plain query log, never referenced by any other table. */
    public int purgeOlderThan(LocalDate cutoff){return jdbc.update("DELETE FROM search_log WHERE created_at<?",Date.valueOf(cutoff));}
}
