package bms.player.beatoraja;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

/** Downloads table metadata for the native launcher; never downloads song files. */
public final class MacTableUpdater {
    public static boolean update(Config config, String[] urls) throws Exception {
        System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
        System.setProperty("sun.net.client.defaultReadTimeout", "15000");
        Path directory = Path.of(config.getTablepath());
        Files.createDirectories(directory);
        ExecutorService workers = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "Table download"); thread.setDaemon(true); return thread;
        });
        JsonValue report = new JsonValue(JsonValue.ValueType.object);
        JsonValue results = new JsonValue(JsonValue.ValueType.array);
        report.addChild("results", results);
        boolean success = true;
        try {
            List<Future<TableData>> tasks = new ArrayList<>();
            for (String url : urls) tasks.add(workers.submit(() ->
                    new TableDataAccessor.DifficultyTableAccessor(directory.toString(), url).read()));
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i];
                JsonValue entry = new JsonValue(JsonValue.ValueType.object);
                entry.addChild("url", new JsonValue(url));
                try {
                    TableData table = tasks.get(i).get(25, TimeUnit.SECONDS);
                    if (table == null || !table.validate()) throw new IllegalStateException("표 데이터를 읽지 못했습니다. 주소 또는 서버 응답을 확인해 주세요.");
                    writeCache(directory, url, table);
                    entry.addChild("name", new JsonValue(table.getName()));
                    entry.addChild("success", new JsonValue(true));
                    System.out.println("난이도표 갱신 완료: " + table.getName());
                } catch (Exception e) {
                    tasks.get(i).cancel(true);
                    success = false;
                    entry.addChild("success", new JsonValue(false));
                    entry.addChild("error", new JsonValue(e instanceof TimeoutException
                            ? "서버 응답 시간이 초과되었습니다."
                            : Objects.toString(e.getMessage(), "다운로드 실패")));
                    System.err.println("난이도표 갱신 실패: " + url);
                }
                results.addChild(entry);
            }
        } finally { workers.shutdownNow(); }
        Files.writeString(Path.of("table-update-result.json"), report.prettyPrint(OutputType.json, 100));
        return success;
    }

    static void writeCache(Path directory, String url, TableData table) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
        Path temporary = Files.createTempFile(directory, ".download-", ".bmt");
        try {
            table.setUrl(url);
            TableData.write(temporary, table);
            if (TableData.read(temporary) == null) throw new IllegalStateException("표 데이터 저장 검증에 실패했습니다.");
            Files.move(temporary, directory.resolve(hash + ".bmt"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
