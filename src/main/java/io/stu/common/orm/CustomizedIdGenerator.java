package io.stu.common.orm;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CustomizedIdGenerator implements IdentifierGenerator {

    private static final char[] CODES = "987abcdefghijk654lmnopqrstuvw321xyz0".toCharArray();

    // start from 2024-01-01 12:00:00.000
    static final long EPOCH = 1704038400000L;

    private static final int STEP_BIT_COUNT = 10;

    private static final long LOW_BITS = (1 << STEP_BIT_COUNT) - 1;

    private static final AtomicLong atomicStep =
        new AtomicLong(ThreadLocalRandom.current().nextLong(0, 1 << STEP_BIT_COUNT));

    public static String getNextId() {
        return encodeDiff(calculateDiff());
    }

    public static String getNextId(String prefix) {
        return prefix + getNextId();
    }

    private static long calculateDiff() {
        return calculateDiff(System.currentTimeMillis(), atomicStep.getAndIncrement());
    }

    private static long calculateDiff(long current, long step) {
        return ((current - EPOCH) << STEP_BIT_COUNT) + (Math.abs(step) & LOW_BITS);
    }

    private static String encodeDiff(long diff) {
        StringBuilder sb = new StringBuilder(8);
        int length = CODES.length;

        while (diff != 0) {
            sb.append(CODES[(int)(diff % length)]);
            diff = diff / length;
        }
        return sb.toString();
    }

    private String toUnderScoreName(String className) {
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(className.charAt(0)));
        for (int i = 1; i < className.length(); i++) {
            char c = className.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_");
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    private String prefix;

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        String id = (String) session.getEntityPersister(null, object).getIdentifier(object, session);
        if (id != null) {
            return id;
        }

        if (prefix == null) {
            prefix = toUnderScoreName(object.getClass().getSimpleName());
        }

        String nextId = null;
        try {
            final IdGenerator idGenerator = DefaultIdGeneratorProvider.INSTANCE.getShare();
            if (idGenerator != null) {
                log.info("Using {}", idGenerator.stat());
                nextId = prefix + idGenerator.generateAsString();
            }
        } catch (Throwable ex) {
            log.warn("Error generating IdGenerator with cosid", ex);
        }

        if (nextId == null) {
            log.info("Using default IdGenerator");
            nextId = prefix + getNextId();
        }

        log.info("Next id: {}", nextId);

        return nextId;
    }

    public static String getNextCosId() {
        String nextId = null;
        try {
            final IdGenerator idGenerator = DefaultIdGeneratorProvider.INSTANCE.getShare();
            if (idGenerator != null) {
                log.info("Using {}", idGenerator.stat());
                nextId = idGenerator.generateAsString();
            }
        } catch (Throwable ex) {
            log.warn("Error generating IdGenerator with cosid", ex);
        }

        if (nextId == null) {
            log.info("Using default IdGenerator");
            nextId = getNextId();
        }

        log.info("Next id: {}", nextId);
        return nextId;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
        EventType eventType) {
        return generate(session, owner);
    }

    @Override
    public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) {
        prefix = parameters.getProperty("prefix");
    }

}