package com.devmanchego.performanceanalyzer.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final ConcurrentHashMap<UUID, AnalysisSession> sessions = new ConcurrentHashMap<>();

    @Value("${performanceanalyzer.session.idle-timeout-minutes:120}")
    private int idleTimeoutMinutes;

    public void put(AnalysisSession session) {
        sessions.put(session.getId(), session);
    }

    public Optional<AnalysisSession> get(UUID id) {
        AnalysisSession session = sessions.get(id);
        if (session != null) session.touch();
        return Optional.ofNullable(session);
    }

    public void remove(UUID id) {
        sessions.remove(id);
    }

    /** Checks presence without refreshing the idle timeout (unlike {@link #get}). */
    public boolean exists(UUID id) {
        return sessions.containsKey(id);
    }

    public int size() {
        return sessions.size();
    }

    /** Removes every session and returns how many were cleared. */
    public int clear() {
        int count = sessions.size();
        sessions.clear();
        return count;
    }

    // Evict idle sessions every 15 minutes
    @Scheduled(fixedDelay = 900_000)
    public void evictExpiredSessions() {
        Instant cutoff = Instant.now().minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().getLastAccessedAt().isBefore(cutoff));
        int evicted = before - sessions.size();
        if (evicted > 0) {
            log.info("Evicted {} expired session(s). Active sessions: {}", evicted, sessions.size());
        }
    }
}
