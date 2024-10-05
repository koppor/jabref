package org.jabref.logic.shared;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jabref.testutils.category.DatabaseTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.SQLException;

@DatabaseTest
class DBMSConnectionTest {

    @ParameterizedTest
    @EnumSource(DBMSType.class)
    void getConnectionFailsWhenconnectingToInvalidHost(DBMSType dbmsType) {
        assertThrows(
                SQLException.class,
                () ->
                        new DBMSConnection(
                                        new DBMSConnectionPropertiesBuilder()
                                                .setType(dbmsType)
                                                .setHost("XXXX")
                                                .setPort(33778)
                                                .setDatabase("XXXX")
                                                .setUser("XXXX")
                                                .setPassword("XXXX")
                                                .setUseSSL(false)
                                                .setServerTimezone("XXXX")
                                                .createDBMSConnectionProperties())
                                .getConnection());
    }
}
