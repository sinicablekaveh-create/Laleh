package com.sinicable.telegramelectric;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;
public class SharedQueryContractTest {
    @Test public void androidAndWebConsumeIdenticalContractFixtures() throws Exception {
        JSONArray rows = new JSONArray(new String(Files.readAllBytes(Paths.get(System.getProperty("laleh.sharedDir"), "query-contract-fixtures.json")), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            SearchQuery result = SearchQuery.parse(row.getString("raw"));
            assertEquals(row.getString("normalized"), result.normalized);
            assertEquals(row.getString("category"), result.category);
            assertEquals(row.getString("location"), result.location);
            assertEquals(row.getString("intent"), result.intent.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
