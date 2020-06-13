package com.gruelbox.transactionoutbox.jooq.acceptance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gruelbox.transactionoutbox.JooqTransaction;
import com.gruelbox.transactionoutbox.ThreadLocalJooqTransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

@Slf4j
class TestUtils {

  private static final Table<Record> TEST_TABLE = DSL.table("TESTDATA");

  static void writeRecord(Configuration configuration, int value) {
    log.info("Inserting record {}", value);
    configuration.dsl().insertInto(TEST_TABLE).values(value).execute();
  }

  static void writeRecord(JooqTransaction transaction, int value) {
    writeRecord(transaction.context(), value);
  }

  static void writeRecord(ThreadLocalJooqTransactionManager transactionManager, int value) {
    transactionManager.requireTransactionReturns(
        tx -> {
          writeRecord(tx, value);
          return null;
        });
  }

  static void assertRecordExists(DSLContext dsl, int value) {
    assertTrue(
        dsl.select()
            .from(TEST_TABLE)
            .where(DSL.field("VALUE").eq(value))
            .fetchOptional()
            .isPresent());
  }

  static void assertRecordNotExists(DSLContext dsl, int value) {
    assertFalse(
        dsl.select()
            .from(TEST_TABLE)
            .where(DSL.field("VALUE").eq(value))
            .fetchOptional()
            .isPresent());
  }
}
